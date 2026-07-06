package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.application.lp.DosFasesUseCase;
import jpap.dev.io_api.application.lp.GraficoUseCase;
import jpap.dev.io_api.application.lp.GranMUseCase;
import jpap.dev.io_api.application.lp.SimplexUseCase;
import jpap.dev.io_api.application.redes.RedUseCase;
import jpap.dev.io_api.application.transporte.TransporteUseCase;
import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.grafico.PuntoVertice;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import jpap.dev.io_api.domain.transporte.CostoPorMetodo;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ejecuta el solver correspondiente una vez que el humano APROBÓ el modelo.
 * Es el único punto de la capa de IA que invoca los use cases de resolución:
 * las @Tool ya no resuelven directamente — solo crean solicitudes de aprobación.
 *
 * Devuelve tanto el resultado estructurado (para la UI) como el resumen en texto
 * que el tutor usa para explicar el procedimiento al estudiante.
 */
@Slf4j
@Component
public class ResolucionEjecutor {

    private final SimplexUseCase simplexUseCase;
    private final GranMUseCase granMUseCase;
    private final DosFasesUseCase dosFasesUseCase;
    private final GraficoUseCase graficoUseCase;
    private final TransporteUseCase transporteUseCase;
    private final RedUseCase redUseCase;

    public ResolucionEjecutor(SimplexUseCase simplexUseCase,
                              GranMUseCase granMUseCase,
                              DosFasesUseCase dosFasesUseCase,
                              GraficoUseCase graficoUseCase,
                              TransporteUseCase transporteUseCase,
                              RedUseCase redUseCase) {
        this.simplexUseCase = simplexUseCase;
        this.granMUseCase = granMUseCase;
        this.dosFasesUseCase = dosFasesUseCase;
        this.graficoUseCase = graficoUseCase;
        this.transporteUseCase = transporteUseCase;
        this.redUseCase = redUseCase;
    }

    /**
     * Resultado de una ejecución aprobada. Solo uno de los cuatro resultados es non-null:
     * resultado (tabular LP: Simplex/GranM/DosFases), resultadoGrafico (método gráfico),
     * resultadoTransporte (métodos de transporte) o resultadoRed (problemas de redes).
     */
    public record Ejecucion(
            SolveResult<SolucionLP> resultado,
            SolveResult<SolucionGrafica> resultadoGrafico,
            SolveResult<SolucionTransporte> resultadoTransporte,
            SolveResult<SolucionRed> resultadoRed,
            String resumenParaTutor
    ) {}

    public Ejecucion ejecutar(ModeloResoluble modelo, MetodoResolucion metodo) {
        log.info("[HITL] ejecutando solver aprobado — metodo={}", metodo);

        if (metodo == MetodoResolucion.TRANSPORTE) {
            ModeloTransporte mt = (ModeloTransporte) modelo;
            SolveResult<SolucionTransporte> resultado = transporteUseCase.resolver(mt);
            return new Ejecucion(null, null, resultado, null, formatearTransporte(resultado, mt));
        }

        if (metodo == MetodoResolucion.REDES) {
            ModeloRed mr = (ModeloRed) modelo;
            SolveResult<SolucionRed> resultado = redUseCase.resolver(mr);
            return new Ejecucion(null, null, null, resultado, formatearRed(resultado, mr));
        }

        ModeloLP mlp = (ModeloLP) modelo;

        if (metodo == MetodoResolucion.GRAFICO) {
            SolveResult<SolucionGrafica> resultado = graficoUseCase.resolver(mlp);
            return new Ejecucion(null, resultado, null, null, formatearGrafico(resultado));
        }

        SolveResult<SolucionLP> resultado = switch (metodo) {
            case SIMPLEX -> simplexUseCase.resolver(mlp);
            case GRAN_M -> granMUseCase.resolver(mlp);
            case DOS_FASES -> dosFasesUseCase.resolver(mlp);
            case GRAFICO -> throw new IllegalStateException("cubierto arriba");
            case TRANSPORTE -> throw new IllegalStateException("cubierto arriba");
            case REDES -> throw new IllegalStateException("cubierto arriba");
        };
        return new Ejecucion(resultado, null, null, null, formatearTabular(resultado, metodo));
    }

    // ─── formato para el tutor (movido desde las @Tool de resolución) ────────────

    private String formatearTabular(SolveResult<SolucionLP> r, MetodoResolucion metodo) {
        String nombre = switch (metodo) {
            case SIMPLEX -> "Simplex estándar";
            case GRAN_M -> "Gran M";
            case DOS_FASES -> "Dos Fases";
            case GRAFICO -> "Gráfico";
            case TRANSPORTE -> "Transporte";   // no se alcanza: transporte se formatea aparte
            case REDES -> "Redes";             // no se alcanza: redes se formatea aparte
        };
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (").append(nombre).append(") ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.NO_ACOTADO) {
            sb.append("El problema NO ESTÁ ACOTADO: la función objetivo crece sin límite.\n");
            sb.append("Pregunta al estudiante si falta alguna restricción que lo limite.\n");
            return sb.toString();
        }

        if (r.status() == SolveStatus.INFACTIBLE) {
            switch (metodo) {
                case GRAN_M -> {
                    sb.append("El problema es INFACTIBLE: ningún punto satisface todas las restricciones.\n");
                    sb.append("Al menos una variable artificial permaneció en la base con valor positivo.\n");
                }
                case DOS_FASES -> {
                    sb.append("El problema es INFACTIBLE: la Fase 1 terminó con w* > 0,\n");
                    sb.append("lo que indica que no existe ningún punto factible.\n");
                }
                default -> sb.append("El problema es INFACTIBLE: ningún punto satisface todas las restricciones simultáneamente.\n");
            }
            return sb.toString();
        }

        if (r.solution() != null) {
            sb.append("Valor óptimo Z* = ").append(r.solution().valorOptimo()).append("\n");
            sb.append("Solución óptima:\n");
            r.solution().valores().forEach((v, val) ->
                    sb.append("  ").append(v).append(" = ").append(val).append("\n"));
        }

        if (r.status() == SolveStatus.MULTIPLE_OPTIMO) {
            sb.append("Nota: existen óptimos múltiples — hay otras soluciones con el mismo Z*.\n");
        }

        if (metodo == MetodoResolucion.DOS_FASES) {
            long pasosFase1 = r.steps().stream().filter(s -> s.titulo().contains("Fase 1")).count();
            long pasosFase2 = r.steps().stream().filter(s -> s.titulo().contains("Fase 2")).count();
            sb.append("\n--- RESUMEN POR FASES ---\n");
            sb.append("Fase 1 (factibilidad): ").append(pasosFase1).append(" pasos\n");
            sb.append("Fase 2 (optimización): ").append(pasosFase2).append(" pasos\n");
        }

        sb.append("\n--- DETALLE DE ITERACIONES (").append(nombre).append(") ---\n");
        for (SolveStep step : r.steps()) {
            sb.append("Paso ").append(step.numero()).append(": ").append(step.titulo()).append("\n");
            Object varEntra = step.datos().get("varEntra");
            Object varSale = step.datos().get("varSale");
            if (varEntra != null) {
                sb.append("  Entra a la base: ").append(varEntra).append("\n");
                sb.append("  Sale de la base: ").append(varSale).append("\n");
            }
            Object base = step.datos().get("base");
            if (base != null) {
                sb.append("  Base resultante: ").append(base).append("\n");
            }
            Object tableau = step.datos().get("tableau");
            if (tableau instanceof List<?> filas && !filas.isEmpty()) {
                List<?> filaZ = (List<?>) filas.get(filas.size() - 1);
                if (!filaZ.isEmpty()) {
                    sb.append("  Valor z acumulado: ").append(filaZ.get(filaZ.size() - 1)).append("\n");
                }
            }
        }

        switch (metodo) {
            case GRAN_M -> {
                sb.append("\nLa interfaz muestra el tableau completo con variables artificiales y penalidad M. ");
                sb.append("Explica al estudiante por qué las artificiales deben salir de la base y ");
                sb.append("pregunta qué observa en la solución óptima.");
            }
            case DOS_FASES -> {
                sb.append("\nLa interfaz muestra el tableau completo con los pasos de Fase 1 y Fase 2. ");
                sb.append("Explica la transición entre fases: la Fase 1 encontró la solución básica factible, ");
                sb.append("y la Fase 2 optimizó el objetivo original desde ahí. ");
                sb.append("Pregunta al estudiante qué diferencia observa entre ambas fases.");
            }
            default -> {
                sb.append("\nLa interfaz ya muestra el tableau. Pregunta al estudiante qué resultado esperaba ");
                sb.append("y qué significa la solución en el contexto del problema real.");
            }
        }

        return sb.toString();
    }

    private String formatearTransporte(SolveResult<SolucionTransporte> r, ModeloTransporte modelo) {
        String nombre = switch (modelo.metodo()) {
            case ESQUINA_NOROESTE -> "Esquina Noroeste";
            case COSTO_MINIMO -> "Costo Mínimo";
            case VOGEL -> "Vogel (VAM)";
            case MODI -> "MODI";
        };
        boolean esOptimo = modelo.metodo() == MetodoTransporte.MODI;

        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (Transporte — ").append(nombre).append(") ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.solution() == null) {
            sb.append("No se obtuvo solución.\n");
            return sb.toString();
        }

        SolucionTransporte sol = r.solution();
        sb.append(esOptimo ? "Costo total ÓPTIMO = " : "Costo de esta solución inicial = ")
          .append(sol.costoTotal()).append("\n");

        if (sol.comparativaInicial() != null && !sol.comparativaInicial().isEmpty()) {
            sb.append("\n--- COMPARATIVA DE MÉTODOS INICIALES ---\n");
            for (CostoPorMetodo c : sol.comparativaInicial()) {
                sb.append("  ").append(c.metodo().name()).append(": costo inicial = ")
                  .append(c.costoInicial()).append("\n");
            }
            if (sol.metodoInicial() != null) {
                sb.append("MODI arrancó desde la solución más barata (")
                  .append(sol.metodoInicial().name()).append(") y la optimizó hasta el óptimo.\n");
            }
        }

        sb.append("\n--- RUTAS UTILIZADAS (asignaciones > 0) ---\n");
        List<String> origenes = sol.origenes();
        List<String> destinos = sol.destinos();
        for (int i = 0; i < sol.asignaciones().size(); i++) {
            List<Double> fila = sol.asignaciones().get(i);
            for (int j = 0; j < fila.size(); j++) {
                double cant = fila.get(j);
                if (cant > 1e-9) {
                    sb.append("  ").append(origenes.get(i)).append(" → ").append(destinos.get(j))
                      .append(": ").append(cant).append(" unidades\n");
                }
            }
        }

        if (r.status() == SolveStatus.MULTIPLE_OPTIMO) {
            sb.append("\nNota: hay ÓPTIMOS MÚLTIPLES — existe una celda no básica con costo reducido 0, ");
            sb.append("por lo que otra asignación distinta alcanza el mismo costo total.\n");
        }

        sb.append("\nLa interfaz ya muestra la tabla de transporte con las asignaciones y los pasos. ");
        if (esOptimo) {
            sb.append("Guía al estudiante para que:\n");
            sb.append("  1. Compare los costos iniciales de los tres métodos y entienda por qué Vogel suele ser el mejor punto de partida.\n");
            sb.append("  2. Interprete los multiplicadores u/v y los costos reducidos: mientras haya uno negativo, la solución mejora.\n");
            sb.append("  3. Siga el ciclo stepping-stone de cada iteración y verifique por qué el óptimo se alcanza cuando todos los costos reducidos son ≥ 0.");
        } else {
            sb.append("Explica que esta es una SOLUCIÓN BÁSICA INICIAL (no necesariamente óptima) y ");
            sb.append("pregunta al estudiante si quiere optimizarla con MODI para hallar el costo mínimo.");
        }

        return sb.toString();
    }

    private String formatearRed(SolveResult<SolucionRed> r, ModeloRed modelo) {
        String nombre = switch (modelo.metodo()) {
            case DIJKSTRA -> "Dijkstra — ruta más corta";
            case KRUSKAL -> "Kruskal — árbol de expansión mínima";
            case EDMONDS_KARP -> "Edmonds-Karp — flujo máximo";
            case FLUJO_COSTO_MINIMO -> "Flujo de costo mínimo";
            case ASIGNACION -> "Asignación (vía red de costo mínimo)";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (Redes — ").append(nombre).append(") ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.INFACTIBLE) {
            switch (modelo.metodo()) {
                case DIJKSTRA -> sb.append("El sumidero NO es alcanzable desde la fuente: no existe ninguna ruta.\n");
                case KRUSKAL -> sb.append("El grafo NO es conexo: hay componentes que ninguna arista une, ")
                        .append("así que no existe árbol de expansión que cubra todos los nodos.\n");
                default -> sb.append("No existe ningún camino con capacidad disponible de la fuente al sumidero.\n");
            }
            sb.append("Pregunta al estudiante si falta alguna arista/arco en el modelo o si los nodos están bien conectados.\n");
            return sb.toString();
        }

        if (r.solution() == null) {
            sb.append("No se obtuvo solución.\n");
            return sb.toString();
        }

        SolucionRed sol = r.solution();
        switch (modelo.metodo()) {
            case DIJKSTRA -> {
                if (sol.rutaOptima() != null) {
                    sb.append("Ruta más corta: ").append(String.join(" → ", sol.rutaOptima()))
                      .append(" con distancia total = ").append(sol.valorObjetivo()).append("\n");
                }
                sb.append("Distancias mínimas desde '").append(modelo.fuente()).append("':\n");
                sol.distancias().forEach((nodo, d) ->
                        sb.append("  ").append(nodo).append(" = ").append(d).append("\n"));
            }
            case KRUSKAL -> {
                sb.append("Peso total del árbol de expansión mínima = ").append(sol.valorObjetivo()).append("\n");
                sb.append("Aristas del árbol:\n");
                for (Arista a : sol.aristasSolucion())
                    sb.append("  ").append(a.origen()).append(" — ").append(a.destino())
                      .append(" (peso ").append(a.peso()).append(")\n");
            }
            case EDMONDS_KARP -> {
                sb.append("Flujo máximo de '").append(modelo.fuente()).append("' a '")
                  .append(modelo.sumidero()).append("' = ").append(sol.valorObjetivo()).append("\n");
                sb.append("Flujo por arco (solo arcos usados):\n");
                sol.flujoPorArco().forEach((arco, f) ->
                        sb.append("  ").append(arco).append(": ").append(f).append(" unidad(es)\n"));
            }
            case FLUJO_COSTO_MINIMO -> {
                sb.append("Flujo máximo = ").append(sol.flujoTotal())
                  .append(" con costo total MÍNIMO = ").append(sol.costoTotal()).append("\n");
                sb.append("Flujo por arco (solo arcos usados):\n");
                sol.flujoPorArco().forEach((arco, f) ->
                        sb.append("  ").append(arco).append(": ").append(f).append(" unidad(es)\n"));
            }
            case ASIGNACION -> {
                sb.append("Costo total MÍNIMO de la asignación = ").append(sol.costoTotal()).append("\n");
                sb.append("Asignación óptima:\n");
                sol.asignacion().forEach((agente, tarea) ->
                        sb.append("  ").append(agente).append(" → ").append(tarea).append("\n"));
            }
        }

        sb.append("\n--- PASOS DEL ALGORITMO ---\n");
        for (SolveStep step : r.steps()) {
            sb.append("Paso ").append(step.numero()).append(": ").append(step.titulo()).append("\n");
        }

        sb.append("\nLa interfaz ya muestra el grafo con la solución resaltada y los pasos. ");
        switch (modelo.metodo()) {
            case DIJKSTRA -> sb.append("Guía al estudiante para que siga el orden en que se asentaron los nodos ")
                    .append("y entienda por qué asentar siempre el de menor distancia provisional garantiza el óptimo ")
                    .append("(y por qué eso falla con pesos negativos).");
            case KRUSKAL -> sb.append("Guía al estudiante para que verifique por qué cada arista aceptada no forma ciclo ")
                    .append("y por qué elegir siempre la más barata disponible produce el árbol de peso mínimo.");
            case EDMONDS_KARP -> sb.append("Guía al estudiante por los caminos de aumento: en cada uno, ")
                    .append("pregúntale cuál es el cuello de botella y qué significa que el arco inverso 'devuelva' flujo. ")
                    .append("Conecta el resultado con el corte mínimo (máx flujo = mín corte).");
            case FLUJO_COSTO_MINIMO -> sb.append("Guía al estudiante por los caminos aumentantes en orden de costo: ")
                    .append("pregúntale por qué se elige siempre el camino más barato de la red residual y qué papel ")
                    .append("juegan los arcos inversos con costo negativo (re-ruteo).");
            case ASIGNACION -> sb.append("Explica la reducción: cada agente y tarea con capacidad 1 fuerza una ")
                    .append("asignación uno-a-uno, y el flujo de costo mínimo elige la combinación más barata. ")
                    .append("Menciona que el método Húngaro llega al mismo óptimo por otro camino.");
        }

        return sb.toString();
    }

    private String formatearGrafico(SolveResult<SolucionGrafica> r) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (Método Gráfico) ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.NO_ACOTADO) {
            sb.append("El problema NO ESTÁ ACOTADO: la región factible se extiende infinitamente\n");
            sb.append("en la dirección de mejora del objetivo.\n");
            sb.append("Pregunta al estudiante si falta alguna restricción que lo limite.\n");
            return sb.toString();
        }

        if (r.status() == SolveStatus.INFACTIBLE) {
            sb.append("El problema es INFACTIBLE: las restricciones no tienen región factible común.\n");
            sb.append("No existe ningún punto (x1,x2) ≥ 0 que satisfaga todas las restricciones.\n");
            return sb.toString();
        }

        if (r.solution() != null) {
            SolucionGrafica sol = r.solution();
            sb.append("Valor óptimo Z* = ").append(sol.valorOptimo()).append("\n");
            sb.append("Solución óptima:\n");
            sol.valores().forEach((v, val) ->
                    sb.append("  ").append(v).append(" = ").append(val).append("\n"));

            sb.append("\nVértices de la región factible con Z evaluado en cada uno:\n");
            for (PuntoVertice v : sol.vertices()) {
                sb.append("  ").append(v.etiqueta())
                  .append("  →  Z = ").append(v.valorZ())
                  .append(v.esOptimo() ? "  ← ÓPTIMO" : "")
                  .append("\n");
            }
        }

        if (r.status() == SolveStatus.MULTIPLE_OPTIMO) {
            sb.append("\nNota: existen ÓPTIMOS MÚLTIPLES — la función objetivo es paralela\n");
            sb.append("a una arista de la región factible, por lo que todos los puntos\n");
            sb.append("sobre esa arista comparten el mismo Z*.\n");
        }

        sb.append("\nLa interfaz ya muestra el gráfico con las líneas de restricción, ");
        sb.append("la región factible sombreada y los vértices marcados (el óptimo con ★).\n");
        sb.append("Guía al estudiante para que:\n");
        sb.append("  1. Identifique cada línea de restricción en el gráfico.\n");
        sb.append("  2. Comprenda por qué la región factible es la intersección de todos los semiplanos.\n");
        sb.append("  3. Entienda por qué el óptimo siempre está en un vértice (Teorema fundamental de la PL).\n");
        sb.append("  4. Verifique manualmente Z en cada vértice para validar el resultado.");

        return sb.toString();
    }
}
