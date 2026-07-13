package jpap.dev.io_api.domain.inventario;

import java.util.List;

/**
 * Validación de entrada de los modelos de inventario. Una entrada malformada
 * (parámetro faltante o fuera de rango) es un error del cliente → IllegalArgumentException
 * (regla del contrato: la excepción se reserva para entradas malformadas, no para
 * resultados; en inventarios determinista no hay resultado "infactible").
 */
public final class InventarioValidador {

    private InventarioValidador() {}

    /** Parámetros comunes a todos los modelos: demanda y costo de ordenar positivos. */
    public static void validarBase(ModeloInventario m) {
        if (m == null) throw new IllegalArgumentException("El modelo de inventario es obligatorio.");
        exigirPositivo(m.demanda(), "la demanda (D)");
        exigirPositivo(m.costoOrden(), "el costo de ordenar/preparar (K)");
    }

    /** EOQ básico y punto de reorden: base + costo de mantener positivo. */
    public static void validarConMantener(ModeloInventario m) {
        validarBase(m);
        exigirPositivo(m.costoMantener(), "el costo de mantener (H)");
    }

    public static void validarProduccion(ModeloInventario m) {
        validarConMantener(m);
        exigirPositivo(m.tasaProduccion(), "la tasa de producción (P)");
        if (m.tasaProduccion() <= m.demanda()) {
            throw new IllegalArgumentException(
                    "La tasa de producción P debe ser mayor que la demanda D (P > D); "
                    + "si P ≤ D la producción no alcanza a cubrir la demanda.");
        }
    }

    public static void validarFaltantes(ModeloInventario m) {
        validarConMantener(m);
        exigirPositivo(m.costoFaltante(), "el costo de faltante (b)");
    }

    public static void validarReorden(ModeloInventario m) {
        validarConMantener(m);
        if (m.leadTimeDias() == null || m.leadTimeDias() < 0) {
            throw new IllegalArgumentException("El tiempo de entrega (leadTimeDias, L) debe ser ≥ 0.");
        }
        if (m.diasHabiles() != null && m.diasHabiles() <= 0) {
            throw new IllegalArgumentException("Los días hábiles del año deben ser > 0.");
        }
    }

    public static void validarDescuentos(ModeloInventario m) {
        validarBase(m);
        List<TramoDescuento> tramos = m.tramos();
        if (tramos == null || tramos.isEmpty()) {
            throw new IllegalArgumentException(
                    "El modelo con descuentos necesita al menos un tramo de precio (tramos).");
        }
        for (TramoDescuento t : tramos) {
            if (t.cantidadMinima() < 0) {
                throw new IllegalArgumentException("La cantidad mínima de un tramo no puede ser negativa.");
            }
            exigirPositivo(t.precioUnitario(), "el precio unitario de cada tramo");
        }
        boolean tieneMantener = m.costoMantener() != null && m.costoMantener() > 0;
        boolean tieneTasa = m.tasaMantenerPorcentaje() != null && m.tasaMantenerPorcentaje() > 0;
        if (!tieneMantener && !tieneTasa) {
            throw new IllegalArgumentException(
                    "Para EOQ con descuentos indica el costo de mantener fijo (costoMantener) "
                    + "o la tasa de mantener como fracción del precio (tasaMantenerPorcentaje).");
        }
    }

    private static void exigirPositivo(Double valor, String nombre) {
        if (valor == null || valor <= 0) {
            throw new IllegalArgumentException("Debes indicar " + nombre + " con un valor positivo.");
        }
    }
}
