package jpap.dev.io_api.domain.transporte.esquinanoroeste;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EsquinaNoroesteSolverTest {

    private static final double DELTA = 1e-5;
    private final EsquinaNoroesteSolver solver = new EsquinaNoroesteSolver();

    /**
     * Problema balanceado 3×3 (oferta 20/30/25, demanda 30/25/20).
     * La esquina noroeste asigna en zig-zag desde arriba-izquierda:
     * x11=20, x21=10, x22=20, x32=5, x33=20 → costo 380 (verificado a mano).
     */
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
                MetodoTransporte.ESQUINA_NOROESTE);
    }

    @Test
    void costo_de_la_solucion_inicial_es_380() {
        SolveResult<SolucionTransporte> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertNotNull(r.solution());
        assertEquals(380.0, r.solution().costoTotal(), DELTA);
    }

    @Test
    void respeta_oferta_y_demanda() {
        SolucionTransporte sol = solver.resolver(problema()).solution();
        assertOfertaDemanda(sol, new double[]{20, 30, 25}, new double[]{30, 25, 20});
    }

    @Test
    void primer_paso_es_la_tabla_inicial_con_datos_de_transporte() {
        SolveResult<SolucionTransporte> r = solver.resolver(problema());
        assertFalse(r.steps().isEmpty());
        assertEquals(0, r.steps().get(0).numero());
        assertEquals("TRANSPORTE", r.steps().get(0).datos().get("tipo"));
        assertNotNull(r.steps().get(0).datos().get("asignaciones"));
    }

    static void assertOfertaDemanda(SolucionTransporte sol, double[] oferta, double[] demanda) {
        int m = sol.origenes().size();
        int n = sol.destinos().size();
        for (int i = 0; i < m; i++) {
            double suma = 0;
            for (int j = 0; j < n; j++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(oferta[i], suma, 1e-5, "fila " + i + " debe sumar la oferta");
        }
        for (int j = 0; j < n; j++) {
            double suma = 0;
            for (int i = 0; i < m; i++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(demanda[j], suma, 1e-5, "columna " + j + " debe sumar la demanda");
        }
    }
}
