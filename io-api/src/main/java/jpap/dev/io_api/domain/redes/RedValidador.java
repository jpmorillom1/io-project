package jpap.dev.io_api.domain.redes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Valida la coherencia del modelo de redes según el método (lanza IllegalArgumentException).
 *
 * Solo la entrada MALFORMADA lanza excepción; la no-factibilidad (grafo desconexo,
 * sumidero inalcanzable, sin camino fuente→sumidero) es un resultado válido que los
 * solvers reportan como SolveStatus.INFACTIBLE.
 *
 * Java puro, sin dependencias de framework.
 */
public final class RedValidador {

    private RedValidador() {}

    public static void validar(ModeloRed modelo) {
        if (modelo.metodo() == null)
            throw new IllegalArgumentException(
                    "El modelo necesita el método de redes (metodo): DIJKSTRA, KRUSKAL, EDMONDS_KARP, "
                    + "FLUJO_COSTO_MINIMO o ASIGNACION.");

        if (modelo.metodo() == MetodoRed.ASIGNACION) {
            validarAsignacion(modelo);
            return;
        }

        validarGrafo(modelo);
        switch (modelo.metodo()) {
            case DIJKSTRA -> validarDijkstra(modelo);
            case KRUSKAL -> validarPesos(modelo);
            case EDMONDS_KARP -> validarFlujo(modelo, false);
            case FLUJO_COSTO_MINIMO -> validarFlujo(modelo, true);
            case ASIGNACION -> { /* cubierto arriba */ }
        }
    }

    // ─── estructura común del grafo ──────────────────────────────────────────────

    private static void validarGrafo(ModeloRed modelo) {
        if (modelo.nodos() == null || modelo.nodos().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos un nodo.");

        Set<String> vistos = new HashSet<>();
        for (String nodo : modelo.nodos()) {
            if (nodo == null || nodo.isBlank())
                throw new IllegalArgumentException("Hay un nodo sin nombre en la lista de nodos.");
            if (!vistos.add(nodo))
                throw new IllegalArgumentException("El nodo '" + nodo + "' está repetido en la lista de nodos.");
        }

        if (modelo.aristas() == null || modelo.aristas().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos una arista.");

        for (int k = 0; k < modelo.aristas().size(); k++) {
            Arista a = modelo.aristas().get(k);
            if (a == null)
                throw new IllegalArgumentException("La arista " + (k + 1) + " está vacía.");
            if (a.origen() == null || a.origen().isBlank() || a.destino() == null || a.destino().isBlank())
                throw new IllegalArgumentException("La arista " + (k + 1) + " necesita nodo origen y nodo destino.");
            if (!vistos.contains(a.origen()))
                throw new IllegalArgumentException(
                        "La arista " + (k + 1) + " referencia el nodo '" + a.origen() + "', que no está en la lista de nodos.");
            if (!vistos.contains(a.destino()))
                throw new IllegalArgumentException(
                        "La arista " + (k + 1) + " referencia el nodo '" + a.destino() + "', que no está en la lista de nodos.");
            if (a.origen().equals(a.destino()))
                throw new IllegalArgumentException(
                        "La arista " + (k + 1) + " conecta '" + a.origen() + "' consigo mismo (lazo); no se admite.");
        }
    }

    // ─── por método ──────────────────────────────────────────────────────────────

    private static void validarDijkstra(ModeloRed modelo) {
        requerirNodo(modelo, modelo.fuente(), "Dijkstra necesita el nodo fuente (origen de la ruta).");
        if (modelo.sumidero() != null && !modelo.nodos().contains(modelo.sumidero()))
            throw new IllegalArgumentException(
                    "El sumidero '" + modelo.sumidero() + "' no está en la lista de nodos.");
        for (Arista a : modelo.aristas()) {
            if (a.peso() == null)
                throw new IllegalArgumentException(
                        "La arista " + a.origen() + "→" + a.destino() + " no tiene peso; Dijkstra necesita el peso de cada arista.");
            if (a.peso() < 0)
                throw new IllegalArgumentException(
                        "La arista " + a.origen() + "→" + a.destino() + " tiene peso negativo (" + a.peso()
                        + "); Dijkstra no admite pesos negativos.");
        }
    }

    private static void validarPesos(ModeloRed modelo) {
        for (Arista a : modelo.aristas())
            if (a.peso() == null)
                throw new IllegalArgumentException(
                        "La arista " + a.origen() + "—" + a.destino() + " no tiene peso; Kruskal necesita el peso de cada arista.");
    }

    private static void validarFlujo(ModeloRed modelo, boolean conCosto) {
        String nombre = conCosto ? "El flujo de costo mínimo" : "El flujo máximo";
        requerirNodo(modelo, modelo.fuente(), nombre + " necesita el nodo fuente.");
        requerirNodo(modelo, modelo.sumidero(), nombre + " necesita el nodo sumidero.");
        if (modelo.fuente().equals(modelo.sumidero()))
            throw new IllegalArgumentException("La fuente y el sumidero deben ser nodos distintos.");
        for (Arista a : modelo.aristas()) {
            if (a.capacidad() == null)
                throw new IllegalArgumentException(
                        "El arco " + a.origen() + "→" + a.destino() + " no tiene capacidad; los problemas de flujo la requieren.");
            if (a.capacidad() < 0)
                throw new IllegalArgumentException(
                        "El arco " + a.origen() + "→" + a.destino() + " tiene capacidad negativa (" + a.capacidad() + ").");
            if (conCosto && a.costo() == null)
                throw new IllegalArgumentException(
                        "El arco " + a.origen() + "→" + a.destino() + " no tiene costo; el flujo de costo mínimo lo requiere.");
        }
    }

    private static void requerirNodo(ModeloRed modelo, String nodo, String mensajeSiFalta) {
        if (nodo == null || nodo.isBlank())
            throw new IllegalArgumentException(mensajeSiFalta);
        if (!modelo.nodos().contains(nodo))
            throw new IllegalArgumentException("El nodo '" + nodo + "' no está en la lista de nodos.");
    }

    private static void validarAsignacion(ModeloRed modelo) {
        if (modelo.agentes() == null || modelo.agentes().isEmpty())
            throw new IllegalArgumentException("Asignación necesita al menos un agente.");
        if (modelo.tareas() == null || modelo.tareas().isEmpty())
            throw new IllegalArgumentException("Asignación necesita al menos una tarea.");

        validarSinRepetidos(modelo.agentes(), "agente");
        validarSinRepetidos(modelo.tareas(), "tarea");

        int n = modelo.agentes().size();
        int m = modelo.tareas().size();
        if (modelo.matrizCostos() == null || modelo.matrizCostos().size() != n)
            throw new IllegalArgumentException("La matriz de costos debe tener " + n + " fila(s), una por agente.");
        for (int i = 0; i < n; i++) {
            List<Double> fila = modelo.matrizCostos().get(i);
            if (fila == null || fila.size() != m)
                throw new IllegalArgumentException(
                        "La fila " + (i + 1) + " de la matriz de costos debe tener " + m + " columna(s), una por tarea.");
            for (int j = 0; j < m; j++)
                if (fila.get(j) == null)
                    throw new IllegalArgumentException(
                            "El costo del agente " + (i + 1) + " para la tarea " + (j + 1) + " está vacío.");
        }
    }

    private static void validarSinRepetidos(List<String> nombres, String tipo) {
        Set<String> vistos = new HashSet<>();
        for (String nombre : nombres) {
            if (nombre == null || nombre.isBlank())
                throw new IllegalArgumentException("Hay un " + tipo + " sin nombre en la lista.");
            if (!vistos.add(nombre))
                throw new IllegalArgumentException("El " + tipo + " '" + nombre + "' está repetido.");
        }
    }
}
