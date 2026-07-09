package jpap.dev.io_api.domain.dinamica;

/**
 * Sentido de la optimización de la función de recurrencia.
 *
 * Es propio de Programación Dinámica y no reutiliza {@code domain.lp.TipoObjetivo}:
 * PD no depende del módulo LP.
 */
public enum SentidoOptimizacion {
    MAXIMIZAR, MINIMIZAR
}
