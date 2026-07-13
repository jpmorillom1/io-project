package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.descuentos.EoqDescuentosSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EoqDescuentosSolverTest {

    private final EoqDescuentosSolver solver = new EoqDescuentosSolver();

    private ModeloInventario modelo(List<TramoDescuento> tramos, Double tasa) {
        return new ModeloInventario(MetodoInventario.EOQ_DESCUENTOS, 5000.0, 49.0, null,
                null, null, null, null, tasa, tramos);
    }

    /**
     * D=5000, K=49, H=20% del precio. Tramos: 0->5.00, 1000->4.80, 2500->4.75.
     * El tramo 4.80 gana: su EOQ (714) queda por debajo de 1000, se sube a 1000 y
     * su costo total (24725) es menor que el de 5.00 (25700) y el de 4.75 (25035.5).
     */
    @Test
    void descuentos_elige_menor_costo_total() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(List.of(
                new TramoDescuento(0, 5.00),
                new TramoDescuento(1000, 4.80),
                new TramoDescuento(2500, 4.75)), 0.2));

        assertEquals(SolveStatus.OPTIMO, res.status());
        SolucionInventario sol = res.solution();
        assertEquals(4.80, sol.precioUnitarioOptimo(), 1e-9);
        assertEquals(1000.0, sol.cantidadOptima(), 1e-6);
        assertEquals(24725.0, sol.costoTotalAnual(), 1e-2);
        // Incluye el costo de compra en el total.
        assertEquals(24000.0, sol.costoCompraAnual(), 1e-2);
        // La comparativa reporta los tres tramos.
        assertEquals(3, sol.comparativa().size());
        assertTrue(sol.comparativa().stream().allMatch(ComparativaTramo::factible));
    }

    @Test
    void sin_tramos_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(List.of(), 0.2)));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(null, 0.2)));
    }

    @Test
    void sin_costo_mantener_ni_tasa_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(
                modelo(List.of(new TramoDescuento(0, 5.0)), null)));
    }
}
