package jpap.dev.io_api.domain.redes.dijkstra;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.RedUtils;
import jpap.dev.io_api.domain.redes.RedValidador;
import jpap.dev.io_api.domain.redes.SolucionRed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Ruta más corta por Dijkstra (pesos ≥ 0). En cada iteración se asienta el nodo
 * pendiente con menor distancia provisional y se relajan sus aristas salientes.
 *
 * Si el modelo trae sumidero, la solución incluye la ruta óptima fuente→sumidero;
 * si no, se reportan las distancias mínimas a todos los nodos alcanzables.
 * Sumidero inalcanzable → SolveStatus.INFACTIBLE (no es una excepción).
 *
 * Java puro, sin dependencias de framework.
 */
public class DijkstraSolver {

    private static final double EPS = 1e-12;

    /** Arco dirigido derivado del modelo (en grafos no dirigidos cada arista genera dos arcos). */
    private record ArcoDir(Arista original, String desde, String hacia, double peso) {}

    private record Entrada(String nodo, double distancia) {}

    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        RedValidador.validar(modelo);

        Map<String, List<ArcoDir>> ady = new HashMap<>();
        for (String n : modelo.nodos()) ady.put(n, new ArrayList<>());
        for (Arista a : modelo.aristas()) {
            ady.get(a.origen()).add(new ArcoDir(a, a.origen(), a.destino(), a.peso()));
            if (!modelo.dirigido())
                ady.get(a.destino()).add(new ArcoDir(a, a.destino(), a.origen(), a.peso()));
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, ArcoDir> previa = new HashMap<>();   // arco con el que se alcanzó cada nodo
        Set<String> asentados = new LinkedHashSet<>();

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(0, "Grafo e inicialización",
                "Dijkstra parte de '" + modelo.fuente() + "' con distancia 0 (∞ para el resto). "
                        + "En cada iteración se asienta el nodo pendiente con menor distancia provisional "
                        + "y se relajan sus aristas salientes.",
                RedUtils.datosBase(modelo)));

        dist.put(modelo.fuente(), 0.0);
        PriorityQueue<Entrada> cola = new PriorityQueue<>(Comparator.comparingDouble(Entrada::distancia));
        cola.add(new Entrada(modelo.fuente(), 0.0));

        while (!cola.isEmpty()) {
            Entrada e = cola.poll();
            if (asentados.contains(e.nodo()) || e.distancia() > dist.get(e.nodo()) + EPS) continue;
            asentados.add(e.nodo());

            List<ArcoDir> relajadas = new ArrayList<>();
            StringBuilder detalle = new StringBuilder();
            for (ArcoDir arco : ady.get(e.nodo())) {
                if (asentados.contains(arco.hacia())) continue;
                double candidata = dist.get(e.nodo()) + arco.peso();
                Double actual = dist.get(arco.hacia());
                if (actual == null || candidata < actual - EPS) {
                    dist.put(arco.hacia(), candidata);
                    previa.put(arco.hacia(), arco);
                    cola.add(new Entrada(arco.hacia(), candidata));
                    relajadas.add(arco);
                    detalle.append(" d(").append(arco.hacia()).append(") = ")
                            .append(RedUtils.round(candidata)).append(".");
                }
            }

            String descripcion = relajadas.isEmpty()
                    ? "Ninguna arista saliente mejora las distancias provisionales."
                    : "Se relajan las aristas salientes de '" + e.nodo() + "':" + detalle;
            pasos.add(new SolveStep(pasos.size(),
                    "Asentar '" + e.nodo() + "' (distancia = " + RedUtils.round(dist.get(e.nodo())) + ")",
                    descripcion,
                    datosPaso(modelo, dist, asentados, previa, relajadas, e.nodo())));
        }

        Map<String, Double> distancias = distanciasOrdenadas(modelo, dist);

        // Con sumidero: la ruta óptima fuente→sumidero (o infactible si es inalcanzable)
        if (modelo.sumidero() != null) {
            if (!dist.containsKey(modelo.sumidero())) {
                pasos.add(new SolveStep(pasos.size(), "El sumidero no es alcanzable",
                        "No existe ningún camino de '" + modelo.fuente() + "' a '" + modelo.sumidero()
                                + "': el sumidero quedó con distancia infinita. El problema es infactible.",
                        datosPaso(modelo, dist, asentados, previa, List.of(), null)));
                return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
            }

            List<String> ruta = new ArrayList<>();
            List<Arista> aristasRuta = new ArrayList<>();
            String actual = modelo.sumidero();
            ruta.add(actual);
            while (!actual.equals(modelo.fuente())) {
                ArcoDir arco = previa.get(actual);
                aristasRuta.add(0, arco.original());
                actual = arco.desde();
                ruta.add(0, actual);
            }
            double distancia = RedUtils.round(dist.get(modelo.sumidero()));

            Map<String, Object> datosF = datosRuta(modelo, distancias, aristasRuta);
            datosF.put("rutaOptima", ruta);
            datosF.put("distancia", distancia);
            pasos.add(new SolveStep(pasos.size(), "Ruta más corta encontrada",
                    "La ruta óptima es " + String.join(" → ", ruta) + " con distancia total " + distancia + ".",
                    datosF));

            return new SolveResult<>(SolveStatus.OPTIMO,
                    new SolucionRed(distancias, ruta, aristasRuta, null, null, distancia, null, null),
                    pasos);
        }

        // Sin sumidero: distancias mínimas a todos los alcanzables (árbol de rutas más cortas)
        List<Arista> arbol = new ArrayList<>();
        for (String n : asentados) {
            ArcoDir p = previa.get(n);
            if (p != null) arbol.add(p.original());
        }
        Map<String, Object> datosF = datosRuta(modelo, distancias, arbol);
        pasos.add(new SolveStep(pasos.size(), "Distancias mínimas calculadas",
                "Se asentaron " + asentados.size() + " de " + modelo.nodos().size()
                        + " nodo(s). Las aristas marcadas forman el árbol de rutas más cortas desde '"
                        + modelo.fuente() + "'.",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                new SolucionRed(distancias, null, arbol, null, null, 0.0, null, null),
                pasos);
    }

    // ─── helpers de pasos ────────────────────────────────────────────────────────

    private Map<String, Object> datosPaso(ModeloRed modelo, Map<String, Double> dist,
                                          Set<String> asentados, Map<String, ArcoDir> previa,
                                          List<ArcoDir> relajadas, String nodoActual) {
        Set<Arista> enArbol = new HashSet<>();
        for (String n : asentados) {
            ArcoDir p = previa.get(n);
            if (p != null) enArbol.add(p.original());
        }
        Set<Arista> activas = new HashSet<>();
        for (ArcoDir arco : relajadas) activas.add(arco.original());

        List<Map<String, Object>> aristas = new ArrayList<>(modelo.aristas().size());
        for (Arista a : modelo.aristas()) {
            String estado = activas.contains(a) ? RedUtils.ESTADO_ACTIVA
                    : enArbol.contains(a) ? RedUtils.ESTADO_SOLUCION
                    : RedUtils.ESTADO_NORMAL;
            aristas.add(RedUtils.arista(a, estado));
        }

        Map<String, Object> datos = RedUtils.datosBase(
                modelo.metodo(), modelo.nodos(), aristas, modelo.fuente(), modelo.sumidero());
        if (nodoActual != null) datos.put("nodoActual", nodoActual);
        datos.put("asentados", new ArrayList<>(asentados));
        datos.put("distancias", distanciasOrdenadas(modelo, dist));
        return datos;
    }

    private Map<String, Object> datosRuta(ModeloRed modelo, Map<String, Double> distancias,
                                          List<Arista> solucion) {
        Set<Arista> enSolucion = new HashSet<>(solucion);
        List<Map<String, Object>> aristas = new ArrayList<>(modelo.aristas().size());
        for (Arista a : modelo.aristas()) {
            aristas.add(RedUtils.arista(a,
                    enSolucion.contains(a) ? RedUtils.ESTADO_SOLUCION : RedUtils.ESTADO_NORMAL));
        }
        Map<String, Object> datos = RedUtils.datosBase(
                modelo.metodo(), modelo.nodos(), aristas, modelo.fuente(), modelo.sumidero());
        datos.put("distancias", distancias);
        return datos;
    }

    /** Distancias redondeadas en el orden de los nodos del modelo (solo los alcanzados). */
    private Map<String, Double> distanciasOrdenadas(ModeloRed modelo, Map<String, Double> dist) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String n : modelo.nodos())
            if (dist.containsKey(n)) out.put(n, RedUtils.round(dist.get(n)));
        return out;
    }
}
