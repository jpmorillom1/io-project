package jpap.dev.io_api.domain.inventario.descuentos;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.inventario.ComparativaTramo;
import jpap.dev.io_api.domain.inventario.InventarioUtils;
import jpap.dev.io_api.domain.inventario.InventarioValidador;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;
import jpap.dev.io_api.domain.inventario.TramoDescuento;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Modelo EOQ con descuentos por cantidad (all-units): el precio unitario de compra baja
 * al pedir más unidades. Como el precio afecta el costo de compra (D·C) y —si el costo de
 * mantener es una fracción del precio— también el de mantener, el óptimo se busca comparando
 * el costo total (compra + ordenar + mantener) de la mejor cantidad de cada tramo.
 *
 * Por cada tramo j con precio C_j:
 *   H_j = i·C_j (si se dio la tasa i) o H fijo;  Q_j = √(2·D·K / H_j).
 *   Se ajusta Q_j al rango del tramo (si el EOQ cae por debajo del mínimo, se sube al mínimo
 *   para calificar al descuento; si cae por encima del rango, ese tramo no puede ser óptimo).
 *   CT_j = D·C_j + (D/Q_j)·K + (Q_j/2)·H_j.  Gana el menor CT_j factible.
 */
public class EoqDescuentosSolver {

    private static final MetodoInventario METODO = MetodoInventario.EOQ_DESCUENTOS;

    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        InventarioValidador.validarDescuentos(modelo);

        double d = modelo.demanda();
        double k = modelo.costoOrden();
        Double i = modelo.tasaMantenerPorcentaje();
        Double hFijo = modelo.costoMantener();

        List<TramoDescuento> tramos = new ArrayList<>(modelo.tramos());
        tramos.sort(Comparator.comparingDouble(TramoDescuento::cantidadMinima));

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Parámetros del modelo",
                String.format("D = %s al año, K = %s. Costo de mantener: %s. Tramos de precio: %s.",
                        fmt(d), fmt(k),
                        i != null ? "H = " + fmt(i) + "·precio (fracción del precio)" : "H = " + fmt(hFijo) + " fijo",
                        describirTramos(tramos)),
                InventarioUtils.datos(METODO)));

        List<ComparativaTramo> comparativa = new ArrayList<>();
        ComparativaTramo mejor = null;
        int numeroPaso = 2;

        for (int j = 0; j < tramos.size(); j++) {
            TramoDescuento tramo = tramos.get(j);
            double precio = tramo.precioUnitario();
            double limiteInferior = tramo.cantidadMinima();
            double limiteSuperior = (j + 1 < tramos.size())
                    ? tramos.get(j + 1).cantidadMinima() : Double.POSITIVE_INFINITY;

            double h = (i != null) ? i * precio : hFijo;
            double eoq = Math.sqrt(2 * d * k / h);

            double q;
            boolean factible;
            String ajuste;
            if (eoq < limiteInferior) {
                q = limiteInferior;                 // sube al mínimo para calificar al descuento
                factible = true;
                ajuste = String.format("EOQ = %s < %s, se sube al mínimo del tramo (%s).",
                        fmt(eoq), fmt(limiteInferior), fmt(limiteInferior));
            } else if (eoq >= limiteSuperior) {
                q = eoq;                            // EOQ fuera del rango: un tramo mayor lo domina
                factible = false;
                ajuste = String.format("EOQ = %s ≥ límite del tramo (%s): no calificable aquí, otro tramo lo mejora.",
                        fmt(eoq), fmt(limiteSuperior));
            } else {
                q = eoq;                            // EOQ dentro del rango
                factible = true;
                ajuste = String.format("EOQ = %s cae dentro del rango del tramo.", fmt(eoq));
            }

            double costoCompra = d * precio;
            double costoOrdenar = (d / q) * k;
            double costoMantener = (q / 2) * h;
            double costoTotal = costoCompra + costoOrdenar + costoMantener;

            ComparativaTramo fila = new ComparativaTramo(
                    InventarioUtils.round2(precio), InventarioUtils.round(q),
                    InventarioUtils.round(costoTotal), factible);
            comparativa.add(fila);

            pasos.add(new SolveStep(numeroPaso++,
                    String.format("Tramo precio %s (desde %s uds)", fmt(precio), fmt(limiteInferior)),
                    String.format("H = %s. %s Q = %s → compra D·C = %s, ordenar = %s, mantener = %s; CT = %s%s",
                            fmt(h), ajuste, fmt(q), fmt(costoCompra), fmt(costoOrdenar), fmt(costoMantener),
                            fmt(costoTotal), factible ? "." : " (no factible)."),
                    InventarioUtils.datosCalculo(METODO, "CT = D·C + (D/Q)·K + (Q/2)·H", null,
                            factible ? costoTotal : null)));

            if (factible && (mejor == null || costoTotal < mejor.costoTotal())) {
                mejor = fila;
            }
        }

        if (mejor == null) {
            // Con all-units siempre hay al menos un tramo factible (el de mínimo 0 / mínimo alcanzable),
            // pero por robustez ante datos raros se reporta como entrada malformada.
            throw new IllegalArgumentException(
                    "Ningún tramo de descuento resultó factible; revisa las cantidades mínimas de los tramos.");
        }

        double precioOptimo = mejor.precioUnitario();
        double qOptimo = mejor.cantidad();
        double hOptimo = (i != null) ? i * precioOptimo : hFijo;
        double costoCompra = d * precioOptimo;
        double costoOrdenar = (d / qOptimo) * k;
        double costoMantener = (qOptimo / 2) * hOptimo;
        double n = d / qOptimo;

        pasos.add(new SolveStep(numeroPaso,
                "Decisión: tramo de menor costo total",
                String.format("El menor costo total factible es %s con precio unitario %s pidiendo %s unidades. "
                                + "Aunque un tramo dé más descuento en la compra, el ahorro puede no compensar; "
                                + "por eso se compara el costo TOTAL, no solo el precio.",
                        fmt(mejor.costoTotal()), fmt(precioOptimo), fmt(qOptimo)),
                InventarioUtils.datosCalculo(METODO, null, null, mejor.costoTotal())));

        String interpretacion = String.format(
                "Pide %s unidades por pedido para pagar el precio unitario de %s: el costo total anual (compra "
                + "%s + ordenar %s + mantener %s = %s) es el menor entre todos los tramos. Harás ≈%s pedidos al año.",
                fmt(qOptimo), fmt(precioOptimo), fmt(costoCompra), fmt(costoOrdenar), fmt(costoMantener),
                fmt(mejor.costoTotal()), fmt(n));

        SolucionInventario solucion = SolucionInventario.builder()
                .cantidadOptima(qOptimo)
                .costoTotalAnual(mejor.costoTotal())
                .costoOrdenarAnual(InventarioUtils.round(costoOrdenar))
                .costoMantenerAnual(InventarioUtils.round(costoMantener))
                .numeroPedidos(InventarioUtils.round(n))
                .costoCompraAnual(InventarioUtils.round(costoCompra))
                .precioUnitarioOptimo(precioOptimo)
                .comparativa(comparativa)
                .interpretacionPolitica(interpretacion)
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private static String describirTramos(List<TramoDescuento> tramos) {
        List<String> partes = new ArrayList<>();
        for (TramoDescuento t : tramos) {
            partes.add(String.format("desde %s uds → %s/ud", fmt(t.cantidadMinima()), fmt(t.precioUnitario())));
        }
        return String.join("; ", partes);
    }

    private static String fmt(double v) {
        return String.valueOf(InventarioUtils.round2(v));
    }
}
