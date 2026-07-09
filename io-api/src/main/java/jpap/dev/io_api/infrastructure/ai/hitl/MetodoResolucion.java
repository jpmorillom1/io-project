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
 * INVENTARIO agrupa los cinco modelos deterministas de inventario (EOQ básico, con descuentos,
 * con faltantes, producción económica y punto de reorden); el submodelo concreto viaja dentro
 * del ModeloInventario.
 * PROGRAMACION_DINAMICA agrupa los cinco submodelos deterministas de PD (asignación de recursos,
 * mochila, ruta por etapas, planificación de producción y reemplazo de equipos); el submodelo
 * concreto viaja dentro del ModeloDinamico.
 */
public enum MetodoResolucion {
    SIMPLEX,
    GRAN_M,
    DOS_FASES,
    GRAFICO,
    TRANSPORTE,
    REDES,
    BRANCH_AND_BOUND,
    INVENTARIO,
    PROGRAMACION_DINAMICA
}
