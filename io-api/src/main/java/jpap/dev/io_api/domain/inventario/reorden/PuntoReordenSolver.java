package jpap.dev.io_api.domain.inventario.reorden;

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
 * Punto de reorden con demanda determinística.
 *
 * Calcula el EOQ básico como política de pedido y, con el tiempo de entrega (lead time L)
 * y la demanda diaria d = D/díasHábiles, el nivel de inventario R = d·L que debe disparar
 * un nuevo pedido para que llegue justo cuando el stock se agota. Si el lead time abarca
 * uno o más ciclos completos, se descuentan (R queda en [0, Q*)).
 */
public class PuntoReordenSolver {

    private static final MetodoInventario METODO = MetodoInventario.PUNTO_REORDEN;

    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        InventarioValidador.validarReorden(modelo);

        double d = modelo.demanda();
        double k = modelo.costoOrden();
        double h = modelo.costoMantener();
        double lead = modelo.leadTimeDias();
        int diasHabiles = modelo.diasHabilesOrDefault();

        double q = Math.sqrt(2 * d * k / h);
        double n = d / q;
        double tDias = diasHabiles / n;
        double demandaDiaria = d / diasHabiles;

        double demandaLead = demandaDiaria * lead;
        // Si el lead time cubre ciclos completos, el pedido pendiente ya cubre esos ciclos:
        // el punto de reorden efectivo es la demanda del lead menos los pedidos en tránsito.
        double ciclosCompletos = Math.floor(demandaLead / q);
        double reorden = demandaLead - ciclosCompletos * q;

        double costoOrdenar = (d / q) * k;
        double costoMantener = (q / 2) * h;
        double costoTotal = costoOrdenar + costoMantener;

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Parámetros del modelo",
                String.format("D = %s al año, K = %s, H = %s por unidad-año, lead time L = %s días, "
                                + "%s días hábiles al año.", fmt(d), fmt(k), fmt(h), fmt(lead), diasHabiles),
                InventarioUtils.datos(METODO)));
        pasos.add(new SolveStep(2, "Cantidad económica de pedido (Q*)",
                String.format("Q* = √(2·D·K/H) = %s unidades; N = D/Q* = %s pedidos al año (cada ≈%s días).",
                        fmt(q), fmt(n), fmt(tDias)),
                InventarioUtils.datosCalculo(METODO, "Q* = raíz(2·D·K/H)", null, q)));
        pasos.add(new SolveStep(3, "Demanda diaria",
                String.format("d = D / días hábiles = %s / %s = %s unidades por día.",
                        fmt(d), diasHabiles, fmt(demandaDiaria)),
                InventarioUtils.datosCalculo(METODO, "d = D / díasHábiles",
                        String.format("%s / %s", fmt(d), diasHabiles), demandaDiaria)));
        String detalleCiclos = ciclosCompletos > 0
                ? String.format(" Como la demanda del lead (%s) supera Q*, se descuentan %s pedido(s) en tránsito.",
                        fmt(demandaLead), fmt(ciclosCompletos))
                : "";
        pasos.add(new SolveStep(4, "Punto de reorden (R)",
                String.format("R = d·L = %s·%s = %s unidades.%s Cuando el inventario baje a %s unidades, "
                                + "coloca un nuevo pedido de Q*.",
                        fmt(demandaDiaria), fmt(lead), fmt(demandaLead), detalleCiclos, fmt(reorden)),
                InventarioUtils.datosCalculo(METODO, "R = d·L",
                        String.format("%s·%s", fmt(demandaDiaria), fmt(lead)), reorden)));
        pasos.add(new SolveStep(5, "Costo total anual",
                String.format("Ordenar = %s, mantener = %s; CT = %s.",
                        fmt(costoOrdenar), fmt(costoMantener), fmt(costoTotal)),
                InventarioUtils.datosCalculo(METODO, "CT = (D/Q*)·K + (Q*/2)·H", null, costoTotal)));

        String interpretacion = String.format(
                "Pide %s unidades cada vez, y coloca el pedido cuando el inventario baje a %s unidades (punto "
                + "de reorden): con una demanda de %s unidades/día y un tiempo de entrega de %s días, ese stock "
                + "restante alcanza justo hasta que llega el nuevo pedido. Harás ≈%s pedidos al año.",
                fmt(q), fmt(reorden), fmt(demandaDiaria), fmt(lead), fmt(n));

        SolucionInventario solucion = SolucionInventario.builder()
                .cantidadOptima(InventarioUtils.round(q))
                .costoTotalAnual(InventarioUtils.round(costoTotal))
                .costoOrdenarAnual(InventarioUtils.round(costoOrdenar))
                .costoMantenerAnual(InventarioUtils.round(costoMantener))
                .numeroPedidos(InventarioUtils.round(n))
                .tiempoCicloDias(InventarioUtils.round(tDias))
                .puntoReorden(InventarioUtils.round(reorden))
                .demandaDiaria(InventarioUtils.round(demandaDiaria))
                .interpretacionPolitica(interpretacion)
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private static String fmt(double v) {
        return String.valueOf(InventarioUtils.round2(v));
    }
}
