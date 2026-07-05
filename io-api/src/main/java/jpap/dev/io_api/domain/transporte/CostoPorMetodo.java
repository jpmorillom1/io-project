package jpap.dev.io_api.domain.transporte;

/**
 * Costo de la solución básica inicial producida por un método.
 * MODI reporta uno por cada método inicial para justificar desde cuál arrancó.
 */
public record CostoPorMetodo(
        MetodoTransporte metodo,
        double costoInicial
) {}
