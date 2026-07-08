package jpap.dev.io_api.domain.inventario.faltantes;

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
 * Modelo EOQ con faltantes planeados (backorders permitidos).
 *
 * Se admite quedarse sin stock parte del ciclo y satisfacer esa demanda después, a un
 * costo de faltante b por unidad y periodo. Pedir más grande y agotar a propósito baja
 * el costo de mantener a cambio del de faltante.
 *   Q* = √(2DK/H)·√((H+b)/b);  S (inv. máx) = Q*·b/(H+b);  faltante máx = Q* − S.
 *   CT = (D/Q)·K + (S²/(2Q))·H + ((Q−S)²/(2Q))·b.
 */
public class EoqFaltantesSolver {

    private static final MetodoInventario METODO = MetodoInventario.EOQ_FALTANTES;

    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        InventarioValidador.validarFaltantes(modelo);

        double d = modelo.demanda();
        double k = modelo.costoOrden();
        double h = modelo.costoMantener();
        double b = modelo.costoFaltante();
        int diasHabiles = modelo.diasHabilesOrDefault();

        double eoqBasico = Math.sqrt(2 * d * k / h);
        double factor = Math.sqrt((h + b) / b);
        double q = eoqBasico * factor;
        double s = q * b / (h + b);                 // inventario máximo
        double faltanteMax = q - s;
        double n = d / q;
        double tDias = diasHabiles / n;
        double costoOrdenar = (d / q) * k;
        double costoMantener = (s * s / (2 * q)) * h;
        double costoFaltante = (faltanteMax * faltanteMax / (2 * q)) * b;
        double costoTotal = costoOrdenar + costoMantener + costoFaltante;

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Parámetros del modelo",
                String.format("D = %s, K = %s, H = %s por unidad-año, costo de faltante b = %s por unidad-año.",
                        fmt(d), fmt(k), fmt(h), fmt(b)),
                InventarioUtils.datos(METODO)));
        pasos.add(new SolveStep(2, "Cantidad óptima de pedido (Q*)",
                String.format("Q* = √(2·D·K/H)·√((H+b)/b) = %s·%s = %s unidades. El factor √((H+b)/b) = %s "
                                + "es el aumento respecto al EOQ básico (%s) por permitir faltantes.",
                        fmt(eoqBasico), fmt(factor), fmt(q), fmt(factor), fmt(eoqBasico)),
                InventarioUtils.datosCalculo(METODO, "Q* = raíz(2·D·K/H)·raíz((H+b)/b)", null, q)));
        pasos.add(new SolveStep(3, "Inventario máximo (S) y faltante máximo",
                String.format("S = Q*·b/(H+b) = %s unidades es el inventario máximo; el faltante máximo "
                                + "planeado es Q* − S = %s unidades.", fmt(s), fmt(faltanteMax)),
                InventarioUtils.datosCalculo(METODO, "S = Q*·b/(H+b)", null, s)));
        pasos.add(new SolveStep(4, "Número de pedidos y tiempo de ciclo",
                String.format("N = D/Q* = %s pedidos al año; el ciclo dura ≈%s días hábiles.", fmt(n), fmt(tDias)),
                InventarioUtils.datosCalculo(METODO, "N = D / Q*", null, n)));
        pasos.add(new SolveStep(5, "Costo total anual",
                String.format("Ordenar = %s, mantener = (S²/2Q)·H = %s, faltante = ((Q−S)²/2Q)·b = %s; CT = %s.",
                        fmt(costoOrdenar), fmt(costoMantener), fmt(costoFaltante), fmt(costoTotal)),
                InventarioUtils.datosCalculo(METODO,
                        "CT = (D/Q)·K + (S²/2Q)·H + ((Q−S)²/2Q)·b", null, costoTotal)));

        String interpretacion = String.format(
                "Pide %s unidades por ciclo pero deja que el inventario llegue solo a %s (el resto, %s unidades, "
                + "se sirve como pedidos pendientes). Harás ≈%s pedidos al año con un costo total de %s. Permitir "
                + "faltantes conviene solo si el costo de faltante b es bajo frente al de mantener H.",
                fmt(q), fmt(s), fmt(faltanteMax), fmt(n), fmt(costoTotal));

        SolucionInventario solucion = SolucionInventario.builder()
                .cantidadOptima(InventarioUtils.round(q))
                .costoTotalAnual(InventarioUtils.round(costoTotal))
                .costoOrdenarAnual(InventarioUtils.round(costoOrdenar))
                .costoMantenerAnual(InventarioUtils.round(costoMantener))
                .numeroPedidos(InventarioUtils.round(n))
                .tiempoCicloDias(InventarioUtils.round(tDias))
                .nivelMaximoInventario(InventarioUtils.round(s))
                .faltanteMaximo(InventarioUtils.round(faltanteMax))
                .costoFaltanteAnual(InventarioUtils.round(costoFaltante))
                .interpretacionPolitica(interpretacion)
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private static String fmt(double v) {
        return String.valueOf(InventarioUtils.round2(v));
    }
}
