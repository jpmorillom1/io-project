package jpap.dev.io_api.domain.inventario;

/**
 * Fila de la comparativa del modelo EOQ con descuentos por cantidad: para el precio
 * {@code precioUnitario} de un tramo, la cantidad de pedido evaluada ({@code cantidad},
 * ya ajustada al rango factible del tramo) y su costo total anual {@code costoTotal}
 * (compra + ordenar + mantener). {@code factible} indica si esa cantidad cae dentro
 * del rango del tramo (una cantidad por debajo del mínimo no da derecho al descuento).
 */
public record ComparativaTramo(
        double precioUnitario,
        double cantidad,
        double costoTotal,
        boolean factible
) {}
