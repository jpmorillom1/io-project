package jpap.dev.io_api.domain.redes;

/**
 * Los cinco problemas de redes soportados. El submétodo viaja dentro del
 * {@link ModeloRed}; para la cadena HITL siempre se usa MetodoResolucion.REDES.
 */
public enum MetodoRed {
    /** Ruta más corta desde una fuente (pesos ≥ 0). */
    DIJKSTRA,
    /** Árbol de expansión mínima sobre grafo no dirigido conexo. */
    KRUSKAL,
    /** Flujo máximo fuente→sumidero por caminos de aumento (BFS). */
    EDMONDS_KARP,
    /** Flujo máximo de costo mínimo fuente→sumidero (successive shortest paths). */
    FLUJO_COSTO_MINIMO,
    /** Asignación agentes→tareas, reducida a flujo de costo mínimo bipartito. */
    ASIGNACION
}
