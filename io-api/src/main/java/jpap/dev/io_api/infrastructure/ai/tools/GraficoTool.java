package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.application.lp.GraficoUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.domain.lp.grafico.PuntoVertice;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Expone el GraficoSolver como @Tool del tutor.
 * Solo aplica cuando el modelo tiene exactamente 2 variables.
 * Escribe en contextStore.resultadoGrafico (campo separado de resultado tabular).
 */
@Slf4j
@Component
public class GraficoTool {

    private final GraficoUseCase graficoUseCase;
    private final ChatContextStore contextStore;

    public GraficoTool(GraficoUseCase graficoUseCase, ChatContextStore contextStore) {
        this.graficoUseCase = graficoUseCase;
        this.contextStore = contextStore;
    }

    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las 2 variables de decisión")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho). Debe ser >= 0.")
            double rhs
    ) {}

    @Tool("""
            Resuelve un problema de Programación Lineal usando el MÉTODO GRÁFICO.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El modelo tenga EXACTAMENTE 2 variables de decisión — no más, no menos.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver usando el método gráfico explícitamente,
                 o haya pedido ver la región factible, el gráfico, o la solución visual.
            RESTRICCIÓN TÉCNICA: falla con más o menos de 2 variables — en ese caso informa
            al estudiante que el método gráfico solo aplica para 2 variables de decisión.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=).
            La interfaz mostrará el gráfico con las líneas de restricción, la región factible
            sombreada y los vértices evaluados. El óptimo se resalta con una estrella.
            """)
    public String resolverGrafico(
            @P("Lista de EXACTAMENTE 2 nombres de variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las 2 variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes' (2 valores), 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverGrafico — vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        SolveResult<SolucionGrafica> resultado = graficoUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[TOOL] resolverGrafico completado — status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        // Campo separado: no pisa el resultado tabular (Simplex/GranM/DosFases)
        contextStore.obtener().resultadoGrafico = resultado;

        return formatearParaTutor(resultado, variables);
    }

    private String formatearParaTutor(SolveResult<SolucionGrafica> r, List<String> vars) {
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
