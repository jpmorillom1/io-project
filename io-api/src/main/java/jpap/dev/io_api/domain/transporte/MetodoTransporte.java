package jpap.dev.io_api.domain.transporte;

/**
 * Método de resolución de un problema de transporte.
 *
 * Los tres primeros construyen una solución básica inicial (BFS);
 * MODI parte de una solución inicial y la optimiza hasta el óptimo.
 */
public enum MetodoTransporte {
    ESQUINA_NOROESTE,
    COSTO_MINIMO,
    VOGEL,
    MODI
}
