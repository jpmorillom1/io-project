package jpap.dev.io_api.domain.redes.asignacion;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.RedUtils;
import jpap.dev.io_api.domain.redes.RedValidador;
import jpap.dev.io_api.domain.redes.SolucionRed;
import jpap.dev.io_api.domain.redes.flujocostominimo.FlujoCostoMinimoSolver;
import jpap.dev.io_api.domain.redes.flujocostominimo.FlujoCostoMinimoSolver.RedMcf;
import jpap.dev.io_api.domain.redes.flujocostominimo.FlujoCostoMinimoSolver.ResultadoMcf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Asignación óptima agentes→tareas por reducción a flujo de costo mínimo (NO Húngaro):
 * se construye la red bipartita unitaria
 *   S → agenteᵢ (cap 1, costo 0) · agenteᵢ → tareaⱼ (cap 1, costo cᵢⱼ) · tareaⱼ → T (cap 1, costo 0)
 * y se delega en el core de {@link FlujoCostoMinimoSolver}. El flujo de valor máx(n,m)
 * y costo mínimo marca con flujo 1 los pares agente→tarea de la asignación óptima.
 *
 * Si n ≠ m se balancea con un agente/tarea "Ficticio" de costo 0 (los pares con
 * ficticio quedan fuera del mapa `asignacion`: representan tareas/agentes sin par).
 *
 * Java puro, sin dependencias de framework.
 */
public class AsignacionSolver {

    private static final double EPS = 1e-9;

    private final FlujoCostoMinimoSolver mcf = new FlujoCostoMinimoSolver();

    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        RedValidador.validar(modelo);

        int n = modelo.agentes().size();
        int m = modelo.tareas().size();
        List<String> agentes = new ArrayList<>(modelo.agentes());
        List<String> tareas = new ArrayList<>(modelo.tareas());

        Set<String> usados = new HashSet<>(agentes);
        usados.addAll(tareas);
        String ficticio = nombreUnico("Ficticio", usados);

        boolean agenteFicticio = n < m;
        boolean tareaFicticia = n > m;
        if (agenteFicticio) agentes.add(ficticio);
        if (tareaFicticia) tareas.add(ficticio);

        // Matriz balanceada: filas/columnas del ficticio quedan con costo 0
        double[][] costos = new double[agentes.size()][tareas.size()];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                costos[i][j] = modelo.matrizCostos().get(i).get(j);

        String fuente = nombreUnico("S", usados);
        String sumidero = nombreUnico("T", usados);

        List<String> nodos = new ArrayList<>();
        nodos.add(fuente);
        nodos.addAll(agentes);
        nodos.addAll(tareas);
        nodos.add(sumidero);

        List<Arista> arcos = new ArrayList<>();
        for (String agente : agentes)
            arcos.add(new Arista(fuente, agente, null, 1.0, 0.0));
        for (int i = 0; i < agentes.size(); i++)
            for (int j = 0; j < tareas.size(); j++)
                arcos.add(new Arista(agentes.get(i), tareas.get(j), null, 1.0, costos[i][j]));
        for (String tarea : tareas)
            arcos.add(new Arista(tarea, sumidero, null, 1.0, 0.0));

        List<SolveStep> pasos = new ArrayList<>();
        Map<String, Object> datos0 = RedUtils.datosBase(MetodoRed.ASIGNACION, nodos,
                aristasSerializadas(arcos, Set.of()), fuente, sumidero);
        datos0.put("agentes", agentes);
        datos0.put("tareas", tareas);
        pasos.add(new SolveStep(0, "Reducción a red de flujo de costo mínimo",
                descripcionBalanceo(n, m, ficticio)
                        + " Se construye la red bipartita unitaria: " + fuente + "→agente (cap 1, costo 0), "
                        + "agente→tarea (cap 1, costo cᵢⱼ) y tarea→" + sumidero + " (cap 1, costo 0). "
                        + "El flujo de costo mínimo de valor " + agentes.size()
                        + " marcará con flujo 1 los pares de la asignación óptima.",
                datos0));

        ResultadoMcf r = mcf.ejecutarCore(new RedMcf(nodos, arcos, fuente, sumidero),
                MetodoRed.ASIGNACION, pasos);

        // Con la red bipartita completa y balanceada siempre hay asignación perfecta;
        // este guardián solo cubre entradas degeneradas inesperadas.
        if (r.flujoTotal() + EPS < agentes.size())
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);

        Set<String> conjuntoAgentes = new HashSet<>(agentes);
        Set<String> conjuntoTareas = new HashSet<>(tareas);
        Map<String, String> asignacion = new LinkedHashMap<>();
        Map<String, Double> flujoPorArco = new LinkedHashMap<>();
        List<Arista> elegidas = new ArrayList<>();
        double costoReal = 0.0;

        for (Arista arco : r.arcosConFlujo()) {
            if (!conjuntoAgentes.contains(arco.origen()) || !conjuntoTareas.contains(arco.destino()))
                continue;   // arcos S→agente y tarea→T: estructura interna de la reducción
            if (arco.origen().equals(ficticio) || arco.destino().equals(ficticio))
                continue;   // el ficticio absorbe al agente/tarea sin par (costo 0)
            asignacion.put(arco.origen(), arco.destino());
            flujoPorArco.put(RedUtils.claveArco(arco.origen(), arco.destino()), 1.0);
            elegidas.add(arco);
            costoReal += arco.costo();
        }

        double costoTotal = RedUtils.round(costoReal);
        StringJoiner pares = new StringJoiner(", ");
        asignacion.forEach((agente, tarea) -> pares.add(agente + " → " + tarea));

        Map<String, Object> datosF = RedUtils.datosBase(MetodoRed.ASIGNACION, nodos,
                aristasSerializadas(arcos, new HashSet<>(elegidas)), fuente, sumidero);
        datosF.put("asignacion", asignacion);
        datosF.put("costoTotal", costoTotal);
        pasos.add(new SolveStep(pasos.size(), "Asignación óptima",
                "Los arcos agente→tarea con flujo 1 forman la asignación óptima: " + pares
                        + ". Costo total mínimo = " + costoTotal + "."
                        + notaFicticio(agenteFicticio, tareaFicticia, ficticio),
                datosF));

        SolucionRed solucion = new SolucionRed(null, null, elegidas, flujoPorArco, asignacion,
                costoTotal, RedUtils.round(r.flujoTotal()), costoTotal);
        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> aristasSerializadas(List<Arista> arcos, Set<Arista> solucion) {
        List<Map<String, Object>> out = new ArrayList<>(arcos.size());
        for (Arista a : arcos)
            out.add(RedUtils.arista(a,
                    solucion.contains(a) ? RedUtils.ESTADO_SOLUCION : RedUtils.ESTADO_NORMAL));
        return out;
    }

    private String descripcionBalanceo(int n, int m, String ficticio) {
        if (n < m)
            return "Hay " + n + " agente(s) para " + m + " tarea(s): se agrega el agente '" + ficticio
                    + "' con costo 0 (la tarea que reciba quedará sin asignar).";
        if (n > m)
            return "Hay " + n + " agente(s) para " + m + " tarea(s): se agrega la tarea '" + ficticio
                    + "' con costo 0 (el agente que la reciba quedará sin asignar).";
        return "El problema está balanceado (" + n + " agentes y " + m + " tareas).";
    }

    private String notaFicticio(boolean agenteFicticio, boolean tareaFicticia, String ficticio) {
        if (agenteFicticio)
            return " La tarea asignada al agente '" + ficticio + "' queda sin responsable real.";
        if (tareaFicticia)
            return " El agente asignado a la tarea '" + ficticio + "' queda sin tarea real.";
        return "";
    }

    private String nombreUnico(String base, Set<String> usados) {
        String nombre = base;
        while (usados.contains(nombre)) nombre += "_";
        usados.add(nombre);
        return nombre;
    }
}
