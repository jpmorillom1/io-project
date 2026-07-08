package jpap.dev.io_api.domain.inventario;

/**
 * Un tramo de la tabla de descuentos por cantidad: a partir de {@code cantidadMinima}
 * unidades por pedido, el precio unitario de compra es {@code precioUnitario}.
 *
 * Los tramos se ordenan por {@code cantidadMinima} ascendente; el primero suele
 * arrancar en 0 (precio base sin descuento).
 */
public record TramoDescuento(
        double cantidadMinima,
        double precioUnitario
) {}
