package jpap.dev.io_api.domain.inventario.eoqbasico;

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
 * Modelo EOQ básico (Cantidad Económica de Pedido de Wilson).
 *
 * Supuestos: demanda D constante y conocida, reposición instantánea, sin faltantes.
 * Q* = √(2DK/H); en el óptimo el costo de ordenar iguala al de mantener, y
 * CT* = √(2DKH).
 */
public class EoqBasicoSolver {

    private static final MetodoInventario METODO = MetodoInventario.EOQ_BASICO;

    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        InventarioValidador.validarConMantener(modelo);

        double d = modelo.demanda();
        double k = modelo.costoOrden();
        double h = modelo.costoMantener();
        int diasHabiles = modelo.diasHabilesOrDefault();

        double q = Math.sqrt(2 * d * k / h);
        double n = d / q;
        double tDias = diasHabiles / n;
        double costoOrdenar = (d / q) * k;
        double costoMantener = (q / 2) * h;
        double costoTotal = costoOrdenar + costoMantener;

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Parámetros del modelo",
                String.format("Demanda anual D = %s, costo de ordenar K = %s, costo de mantener H = %s por unidad-año.",
                        fmt(d), fmt(k), fmt(h)),
                InventarioUtils.datos(METODO)));
        pasos.add(new SolveStep(2, "Cantidad económica de pedido (Q*)",
                String.format("Q* = √(2·D·K / H) = √(2·%s·%s / %s) = %s unidades.",
                        fmt(d), fmt(k), fmt(h), fmt(q)),
                InventarioUtils.datosCalculo(METODO, "Q* = raíz(2·D·K / H)",
                        String.format("raíz(2·%s·%s / %s)", fmt(d), fmt(k), fmt(h)), q)));
        pasos.add(new SolveStep(3, "Número de pedidos y tiempo de ciclo",
                String.format("N = D/Q* = %s pedidos al año; el ciclo dura T = %s/N = %s días hábiles.",
                        fmt(n), diasHabiles, fmt(tDias)),
                InventarioUtils.datosCalculo(METODO, "N = D / Q*",
                        String.format("%s / %s", fmt(d), fmt(q)), n)));
        pasos.add(new SolveStep(4, "Costo total anual",
                String.format("Costo de ordenar = (D/Q*)·K = %s; costo de mantener = (Q*/2)·H = %s; "
                                + "CT = %s. En el óptimo ambos costos coinciden (CT = √(2·D·K·H)).",
                        fmt(costoOrdenar), fmt(costoMantener), fmt(costoTotal)),
                InventarioUtils.datosCalculo(METODO, "CT = (D/Q*)·K + (Q*/2)·H", null, costoTotal)));

        String interpretacion = String.format(
                "Pide %s unidades cada vez que el inventario se agote: realizarás ≈%s pedidos al año, "
                + "uno cada ≈%s días hábiles, con un costo total de operación de %s al año (sin contar la compra).",
                fmt(q), fmt(n), fmt(tDias), fmt(costoTotal));

        SolucionInventario solucion = SolucionInventario.builder()
                .cantidadOptima(InventarioUtils.round(q))
                .costoTotalAnual(InventarioUtils.round(costoTotal))
                .costoOrdenarAnual(InventarioUtils.round(costoOrdenar))
                .costoMantenerAnual(InventarioUtils.round(costoMantener))
                .numeroPedidos(InventarioUtils.round(n))
                .tiempoCicloDias(InventarioUtils.round(tDias))
                .interpretacionPolitica(interpretacion)
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private static String fmt(double v) {
        return String.valueOf(InventarioUtils.round2(v));
    }
}
