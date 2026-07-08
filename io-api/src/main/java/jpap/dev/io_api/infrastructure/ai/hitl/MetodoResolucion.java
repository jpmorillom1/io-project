package jpap.dev.io_api.infrastructure.ai.hitl;

/**
 * Método de resolución que el tutor propone ejecutar.
 * La solicitud de aprobación humana lleva este método para que el estudiante
 * sepa exactamente qué algoritmo se ejecutará si aprueba.
 *
 * TRANSPORTE agrupa los cuatro métodos de transporte; el submétodo concreto
 * (Esquina Noroeste, Costo Mínimo, Vogel o MODI) viaja dentro del ModeloTransporte.
 * REDES agrupa los cinco problemas de redes; el submétodo concreto (Dijkstra,
 * Kruskal, Edmonds-Karp, Flujo de Costo Mínimo o Asignación) viaja dentro del ModeloRed.
 * BRANCH_AND_BOUND resuelve Programación Lineal Entera (variables enteras/binarias); el
 * detalle de integralidad viaja dentro del ModeloEntero.
 */
public enum MetodoResolucion {
    SIMPLEX,
    GRAN_M,
    DOS_FASES,
    GRAFICO,
    TRANSPORTE,
    REDES,
    BRANCH_AND_BOUND
}
