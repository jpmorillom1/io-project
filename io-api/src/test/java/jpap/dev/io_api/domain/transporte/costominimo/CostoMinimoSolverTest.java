package jpap.dev.io_api.domain.transporte.costominimo;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CostoMinimoSolverTest {

    private static final double DELTA = 1e-5;
    private final CostoMinimoSolver solver = new CostoMinimoSolver();

    /** Mismo problema 3×3; el método de costo mínimo obtiene 240 (verificado a mano). */
    private ModeloTransporte problema() {
        return new ModeloTransporte(
                List.of("O1", "O2", "O3"),
                List.of("D1", "D2", "D3"),
                List.of(20.0, 30.0, 25.0),
                List.of(30.0, 25.0, 20.0),
                List.of(
                        List.of(4.0, 6.0, 8.0),
                        List.of(6.0, 4.0, 2.0),
                        List.of(2.0, 8.0, 6.0)),
                MetodoTransporte.COSTO_MINIMO);
    }

    @Test
    void costo_de_la_solucion_inicial_es_240() {
        SolveResult<SolucionTransporte> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertNotNull(r.solution());
        assertEquals(240.0, r.solution().costoTotal(), DELTA);
    }

    @Test
    void respeta_oferta_y_demanda() {
        SolucionTransporte sol = solver.resolver(problema()).solution();
        int m = sol.origenes().size();
        int n = sol.destinos().size();
        double[] oferta = {20, 30, 25};
        double[] demanda = {30, 25, 20};
        for (int i = 0; i < m; i++) {
            double suma = 0;
            for (int j = 0; j < n; j++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(oferta[i], suma, DELTA);
        }
        for (int j = 0; j < n; j++) {
            double suma = 0;
            for (int i = 0; i < m; i++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(demanda[j], suma, DELTA);
        }
    }

    @Test
    void es_al_menos_tan_bueno_como_la_esquina_noroeste() {
        double cm = solver.resolver(problema()).solution().costoTotal();
        assertTrue(cm <= 380.0, "costo mínimo (" + cm + ") no debería superar a la esquina noroeste (380)");
    }
}
