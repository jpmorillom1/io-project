package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.reorden.PuntoReordenSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PuntoReordenSolverTest {

    private final PuntoReordenSolver solver = new PuntoReordenSolver();

    private ModeloInventario modelo(Double lead, Integer diasHabiles) {
        return new ModeloInventario(MetodoInventario.PUNTO_REORDEN, 1200.0, 40.0, 3.0,
                null, null, lead, diasHabiles, null, null);
    }

    /** D=1200, K=40, H=3, L=10, 360 dias -> Q*=178.89, d=3.33, R=33.33. */
    @Test
    void reorden_valores_conocidos() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(10.0, 360));

        assertEquals(SolveStatus.OPTIMO, res.status());
        SolucionInventario sol = res.solution();
        assertEquals(178.885438, sol.cantidadOptima(), 1e-4);
        assertEquals(3.333333, sol.demandaDiaria(), 1e-4);
        assertEquals(33.333333, sol.puntoReorden(), 1e-4);
    }

    /** Sin dias habiles se usa el default (360): mismo resultado. */
    @Test
    void dias_habiles_default_360() {
        SolveResult<SolucionInventario> res = solver.resolver(modelo(10.0, null));
        assertEquals(3.333333, res.solution().demandaDiaria(), 1e-4);
        assertEquals(33.333333, res.solution().puntoReorden(), 1e-4);
    }

    @Test
    void sin_lead_time_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(null, 360)));
    }
}
