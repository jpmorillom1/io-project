package jpap.dev.io_api.domain.inventario.produccion;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.inventario.InventarioUtils;
import jpap.dev.io_api.domain.inventario.InventarioValidador;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de producción económica (POQ / EPQ, Economic Production Quantity).
 *
 * A diferencia del EOQ básico, la reposición es GRADUAL: se produce a una tasa P &gt; D
 * mientras la demanda D consume el inventario. El inventario máximo no es Q sino
 * Imax = Q·(1 − D/P). El factor (1 − D/P) reduce el costo de mantener respecto al EOQ.
 *   Q* = √(2DK / (H·(1 − D/P))); Imax = Q*·(1 − D/P); CT = (D/Q)·K + (Imax/2)·H.
 */
public class ProduccionEconomicaSolver {

    private static final MetodoInventario METODO = MetodoInventario.PRODUCCION_ECONOMICA;

    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        InventarioValidador.validarProduccion(modelo);

        double d = modelo.demanda();
        double k = modelo.costoOrden();
        double h = modelo.costoMantener();
        double p = modelo.tasaProduccion();
        int diasHabiles = modelo.diasHabilesOrDefault();

        double factor = 1 - d / p;                 // > 0 porque P > D (validado)
        double q = Math.sqrt(2 * d * k / (h * factor));
        double imax = q * factor;
        double n = d / q;
        double tDias = diasHabiles / n;
        double costoOrdenar = (d / q) * k;
        double costoMantener = (imax / 2) * h;
        double costoTotal = costoOrdenar + costoMantener;

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Parámetros del modelo",
                String.format("D = %s, K = %s, H = %s por unidad-año, tasa de producción P = %s (P > D).",
                        fmt(d), fmt(k), fmt(h), fmt(p)),
                InventarioUtils.datos(METODO)));
        pasos.add(new SolveStep(2, "Factor de reposición gradual",
                String.format("1 − D/P = 1 − %s/%s = %s. Como se produce mientras se consume, el inventario "
                                + "sube más lento que en el EOQ básico.", fmt(d), fmt(p), fmt(factor)),
                InventarioUtils.datosCalculo(METODO, "1 − D/P",
                        String.format("1 − %s/%s", fmt(d), fmt(p)), factor)));
        pasos.add(new SolveStep(3, "Cantidad económica de producción (Q*)",
                String.format("Q* = √(2·D·K / (H·(1 − D/P))) = %s unidades por lote.", fmt(q)),
                InventarioUtils.datosCalculo(METODO, "Q* = raíz(2·D·K / (H·(1 − D/P)))", null, q)));
        pasos.add(new SolveStep(4, "Inventario máximo",
                String.format("Imax = Q*·(1 − D/P) = %s·%s = %s unidades (nunca se acumula todo el lote).",
                        fmt(q), fmt(factor), fmt(imax)),
                InventarioUtils.datosCalculo(METODO, "Imax = Q*·(1 − D/P)", null, imax)));
        pasos.add(new SolveStep(5, "Número de corridas y costo total anual",
                String.format("N = D/Q* = %s corridas al año (cada ≈%s días); costo de preparar = %s, "
                                + "costo de mantener = (Imax/2)·H = %s; CT = %s.",
                        fmt(n), fmt(tDias), fmt(costoOrdenar), fmt(costoMantener), fmt(costoTotal)),
                InventarioUtils.datosCalculo(METODO, "CT = (D/Q*)·K + (Imax/2)·H", null, costoTotal)));

        String interpretacion = String.format(
                "Produce lotes de %s unidades a una tasa de %s/año: harás ≈%s corridas al año. El inventario "
                + "nunca supera %s unidades porque la demanda consume parte mientras produces, con un costo "
                + "total de %s al año.", fmt(q), fmt(p), fmt(n), fmt(imax), fmt(costoTotal));

        SolucionInventario solucion = SolucionInventario.builder()
                .cantidadOptima(InventarioUtils.round(q))
                .costoTotalAnual(InventarioUtils.round(costoTotal))
                .costoOrdenarAnual(InventarioUtils.round(costoOrdenar))
                .costoMantenerAnual(InventarioUtils.round(costoMantener))
                .numeroPedidos(InventarioUtils.round(n))
                .tiempoCicloDias(InventarioUtils.round(tDias))
                .nivelMaximoInventario(InventarioUtils.round(imax))
                .interpretacionPolitica(interpretacion)
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private static String fmt(double v) {
        return String.valueOf(InventarioUtils.round2(v));
    }
}
