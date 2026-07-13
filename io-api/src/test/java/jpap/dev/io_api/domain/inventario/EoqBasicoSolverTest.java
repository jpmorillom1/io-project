package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.eoqbasico.EoqBasicoSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EoqBasicoSolverTest {

    private final EoqBasicoSolver solver = new EoqBasicoSolver();

    private ModeloInventario modelo(Double d, Double k, Double h) {
        return new ModeloInventario(MetodoInventario.EOQ_BASICO, d, k, h,
                null, null, null, null, null, null);
    }

    /** Caso clasico: D=1000, K=50, H=4 -> Q*=158.11, CT=632.46, ordenar=mantener. */
    @Test
    void eoq_basico_valores_conocidos() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(1000.0, 50.0, 4.0));

        assertEquals(SolveStatus.OPTIMO, res.status());
        SolucionInventario sol = res.solution();
        assertEquals(158.113883, sol.cantidadOptima(), 1e-4);
        assertEquals(6.324555, sol.numeroPedidos(), 1e-4);
        assertEquals(632.455532, sol.costoTotalAnual(), 1e-4);
        // En el optimo el costo de ordenar iguala al de mantener.
        assertEquals(sol.costoOrdenarAnual(), sol.costoMantenerAnual(), 1e-4);
        assertEquals(56.921, sol.tiempoCicloDias(), 1e-2);
        assertFalse(res.steps().isEmpty());
    }

    @Test
    void entrada_malformada_sin_costo_mantener_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(1000.0, 50.0, null)));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(-5.0, 50.0, 4.0)));
    }
}
