package jpap.dev.io_api.domain.inventario;

/**
 * Modelos deterministas de inventario soportados por el módulo.
 *
 *   - EOQ_BASICO:           EOQ clásico (demanda constante, sin faltantes, reposición instantánea)
 *   - EOQ_DESCUENTOS:       EOQ con descuentos por cantidad (tabla de precios por tramo)
 *   - EOQ_FALTANTES:        EOQ con faltantes/pedidos pendientes permitidos (backorders)
 *   - PRODUCCION_ECONOMICA: POQ/EPQ, tasa de producción finita P &gt; D (reposición gradual)
 *   - PUNTO_REORDEN:        EOQ + punto de reorden R con tiempo de entrega (lead time)
 */
public enum MetodoInventario {
    EOQ_BASICO,
    EOQ_DESCUENTOS,
    EOQ_FALTANTES,
    PRODUCCION_ECONOMICA,
    PUNTO_REORDEN
}
