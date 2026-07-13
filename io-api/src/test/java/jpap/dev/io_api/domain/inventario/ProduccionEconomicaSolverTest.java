package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.produccion.ProduccionEconomicaSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProduccionEconomicaSolverTest {

    private final ProduccionEconomicaSolver solver = new ProduccionEconomicaSolver();

    private ModeloInventario modelo(double p) {
        return new ModeloInventario(MetodoInventario.PRODUCCION_ECONOMICA, 1000.0, 50.0, 4.0,
                null, p, null, null, null, null);
    }

    /** D=1000, K=50, H=4, P=2000 -> Q*=223.61, Imax=111.80, CT=447.21. */
    @Test
    void poq_valores_conocidos() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(2000.0));

        assertEquals(SolveStatus.OPTIMO, res.status());
        SolucionInventario sol = res.solution();
        assertEquals(223.606798, sol.cantidadOptima(), 1e-4);
        assertEquals(111.803399, sol.nivelMaximoInventario(), 1e-4);
        assertEquals(447.213595, sol.costoTotalAnual(), 1e-4);
        // El inventario maximo es menor que el lote por la reposicion gradual.
        assertTrue(sol.nivelMaximoInventario() < sol.cantidadOptima());
    }

    @Test
    void tasa_produccion_menor_o_igual_a_demanda_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(1000.0)));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(800.0)));
    }
}
