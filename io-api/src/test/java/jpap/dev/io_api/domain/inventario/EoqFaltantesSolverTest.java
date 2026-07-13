package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.faltantes.EoqFaltantesSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EoqFaltantesSolverTest {

    private final EoqFaltantesSolver solver = new EoqFaltantesSolver();

    private ModeloInventario modelo(Double b) {
        return new ModeloInventario(MetodoInventario.EOQ_FALTANTES, 1000.0, 50.0, 4.0,
                b, null, null, null, null, null);
    }

    /** D=1000, K=50, H=4, b=10 -> Q*=187.08, S=133.63, faltante=53.45, CT=534.52. */
    @Test
    void faltantes_valores_conocidos() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(10.0));

        assertEquals(SolveStatus.OPTIMO, res.status());
        SolucionInventario sol = res.solution();
        assertEquals(187.082869, sol.cantidadOptima(), 1e-4);
        assertEquals(133.630621, sol.nivelMaximoInventario(), 1e-4);
        assertEquals(53.452248, sol.faltanteMaximo(), 1e-4);
        assertEquals(534.522484, sol.costoTotalAnual(), 1e-4);
        // Identidad: inventario maximo + faltante maximo = tamano del pedido.
        assertEquals(sol.cantidadOptima(),
                sol.nivelMaximoInventario() + sol.faltanteMaximo(), 1e-4);
        // El pedido con faltantes es mayor que el EOQ basico (sqrt(2*1000*50/4)=158.11).
        assertTrue(sol.cantidadOptima() > 158.11);
    }

    @Test
    void sin_costo_faltante_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(null)));
    }
}
