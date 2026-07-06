package jpap.dev.io_api.domain.redes.edmondskarp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.RedUtils;
import jpap.dev.io_api.domain.redes.RedValidador;
import jpap.dev.io_api.domain.redes.SolucionRed;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flujo máximo por Edmonds-Karp: BFS de caminos de aumento sobre la red residual.
 * Cada arco directo tiene un arco inverso de capacidad 0 que permite "devolver"
 * flujo enviado antes (re-ruteo).
 *
 * Sin ningún camino fuente→sumidero con capacidad disponible → SolveStatus.INFACTIBLE.
 *
 * Java puro, sin dependencias de framework.
 */
public class EdmondsKarpSolver {

    private static final double EPS = 1e-9;

    /** Arco de la red residual. Los inversos tienen original == null y residual inicial 0. */
    private static final class Arco {
        final Arista original;
        final String desde;
        final String hacia;
        double residual;
        Arco inverso;

        Arco(Arista original, String desde, String hacia, double residual) {
            this.original = original;
            this.desde = desde;
            this.hacia = hacia;
            this.residual = residual;
        }

        /** Flujo neto enviado por el arco directo (acumulado en la capacidad del inverso). */
        double flujo() {
            return original != null ? inverso.residual : 0.0;
        }
    }

    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        RedValidador.validar(modelo);

        Map<String, List<Arco>> ady = new HashMap<>();
        for (String n : modelo.nodos()) ady.put(n, new ArrayList<>());
        List<Arco> directos = new ArrayList<>(modelo.aristas().size());
        for (Arista a : modelo.aristas()) {
            Arco directo = new Arco(a, a.origen(), a.destino(), a.capacidad());
            Arco inverso = new Arco(null, a.destino(), a.origen(), 0.0);
            directo.inverso = inverso;
            inverso.inverso = directo;
            ady.get(a.origen()).add(directo);
            ady.get(a.destino()).add(inverso);
            directos.add(directo);
        }

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(0, "Red residual inicial",
                "Se busca el flujo máximo de '" + modelo.fuente() + "' a '" + modelo.sumidero()
                        + "'. Cada arco parte con residual = capacidad; su inverso (capacidad 0) permitirá "
                        + "devolver flujo si conviene re-rutear. En cada iteración un BFS encuentra el camino "
                        + "de aumento con menos arcos.",
                RedUtils.datosBase(modelo)));

        double flujoTotal = 0.0;
        int iteracion = 0;

        while (true) {
            Map<String, Arco> llegada = bfs(modelo, ady);
            if (!llegada.containsKey(modelo.sumidero())) break;

            List<Arco> camino = reconstruir(llegada, modelo.fuente(), modelo.sumidero());
            double cuello = Double.MAX_VALUE;
            for (Arco arco : camino) cuello = Math.min(cuello, arco.residual);

            for (Arco arco : camino) {
                arco.residual -= cuello;
                arco.inverso.residual += cuello;
            }
            flujoTotal += cuello;
            iteracion++;

            List<String> nodosCamino = nodosDe(camino, modelo.fuente());
            boolean usaInverso = camino.stream().anyMatch(arco -> arco.original == null);

            Map<String, Object> datos = datosPaso(modelo, directos, camino);
            datos.put("camino", nodosCamino);
            datos.put("cuelloBotella", RedUtils.round(cuello));
            datos.put("flujoTotal", RedUtils.round(flujoTotal));
            pasos.add(new SolveStep(pasos.size(),
                    "Camino de aumento " + iteracion + ": " + String.join(" → ", nodosCamino),
                    "Cuello de botella = " + RedUtils.round(cuello) + " (el menor residual del camino): se envían "
                            + RedUtils.round(cuello) + " unidad(es). Flujo total acumulado = "
                            + RedUtils.round(flujoTotal) + "."
                            + (usaInverso ? " El camino usa un arco inverso: se cancela flujo enviado antes (re-ruteo)." : ""),
                    datos));
        }

        if (flujoTotal <= EPS) {
            pasos.add(new SolveStep(pasos.size(), "No existe camino fuente→sumidero",
                    "El BFS no encontró ningún camino con capacidad disponible de '" + modelo.fuente()
                            + "' a '" + modelo.sumidero() + "': el flujo máximo es 0 y el problema es infactible.",
                    RedUtils.datosBase(modelo)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        Map<String, Double> flujoPorArco = new LinkedHashMap<>();
        List<Arista> conFlujo = new ArrayList<>();
        for (Arco arco : directos) {
            if (arco.flujo() > EPS) {
                flujoPorArco.merge(RedUtils.claveArco(arco.desde, arco.hacia),
                        RedUtils.round(arco.flujo()), Double::sum);
                conFlujo.add(arco.original);
            }
        }

        double flujoMax = RedUtils.round(flujoTotal);
        Map<String, Object> datosF = datosPaso(modelo, directos, List.of());
        datosF.put("flujoTotal", flujoMax);
        pasos.add(new SolveStep(pasos.size(), "Flujo máximo alcanzado",
                "Ya no queda ningún camino de aumento: el flujo máximo de '" + modelo.fuente() + "' a '"
                        + modelo.sumidero() + "' es " + flujoMax + " (iteraciones: " + iteracion + ").",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                new SolucionRed(null, null, conFlujo, flujoPorArco, null, flujoMax, flujoMax, null),
                pasos);
    }

    // ─── internos ────────────────────────────────────────────────────────────────

    /** BFS sobre arcos con residual > 0; devuelve el arco de llegada a cada nodo alcanzado. */
    private Map<String, Arco> bfs(ModeloRed modelo, Map<String, List<Arco>> ady) {
        Map<String, Arco> llegada = new HashMap<>();
        Set<String> visitados = new HashSet<>();
        visitados.add(modelo.fuente());
        Deque<String> cola = new ArrayDeque<>();
        cola.add(modelo.fuente());

        while (!cola.isEmpty()) {
            String nodo = cola.poll();
            if (nodo.equals(modelo.sumidero())) break;
            for (Arco arco : ady.get(nodo)) {
                if (arco.residual > EPS && visitados.add(arco.hacia)) {
                    llegada.put(arco.hacia, arco);
                    cola.add(arco.hacia);
                }
            }
        }
        return llegada;
    }

    private List<Arco> reconstruir(Map<String, Arco> llegada, String fuente, String sumidero) {
        List<Arco> camino = new ArrayList<>();
        String actual = sumidero;
        while (!actual.equals(fuente)) {
            Arco arco = llegada.get(actual);
            camino.add(0, arco);
            actual = arco.desde;
        }
        return camino;
    }

    private List<String> nodosDe(List<Arco> camino, String fuente) {
        List<String> nodos = new ArrayList<>();
        nodos.add(fuente);
        for (Arco arco : camino) nodos.add(arco.hacia);
        return nodos;
    }

    private Map<String, Object> datosPaso(ModeloRed modelo, List<Arco> directos, List<Arco> camino) {
        Set<Arista> activas = new HashSet<>();
        for (Arco arco : camino)
            activas.add(arco.original != null ? arco.original : arco.inverso.original);

        List<Map<String, Object>> aristas = new ArrayList<>(directos.size());
        for (Arco arco : directos) {
            String estado = activas.contains(arco.original) ? RedUtils.ESTADO_ACTIVA
                    : arco.flujo() > EPS ? RedUtils.ESTADO_SOLUCION
                    : RedUtils.ESTADO_NORMAL;
            aristas.add(RedUtils.arista(arco.original, estado, arco.flujo()));
        }
        return RedUtils.datosBase(modelo.metodo(), modelo.nodos(), aristas,
                modelo.fuente(), modelo.sumidero());
    }
}
