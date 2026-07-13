package jpap.dev.io_api.domain.transporte.vogel;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VogelSolverTest {

    private static final double DELTA = 1e-5;
    private final VogelSolver solver = new VogelSolver();

    /** Mismo problema 3×3; Vogel obtiene 240 (verificado a mano, coincide con el óptimo). */
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
                MetodoTransporte.VOGEL);
    }

    @Test
    void costo_de_la_solucion_inicial_es_240() {
        SolveResult<SolucionTransporte> r = solver.resolver(problema());
        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(240.0, r.solution().costoTotal(), DELTA);
    }

    @Test
    void balancea_agregando_origen_ficticio_cuando_falta_oferta() {
        // Σoferta=15 < Σdemanda=20 → se agrega un ORIGEN ficticio con oferta 5.
        ModeloTransporte desbalanceado = new ModeloTransporte(
                List.of("O1", "O2"),
                List.of("D1", "D2"),
                List.of(10.0, 5.0),
                List.of(10.0, 10.0),
                List.of(
                        List.of(2.0, 4.0),
                        List.of(3.0, 1.0)),
                MetodoTransporte.VOGEL);

        SolucionTransporte sol = solver.resolver(desbalanceado).solution();

        assertEquals(3, sol.origenes().size(), "debe agregarse un origen ficticio");
        assertTrue(sol.origenes().contains("Ficticio"));
        assertEquals(2, sol.destinos().size());
        // La columna de cada destino se cubre completamente (incluyendo lo que aporta el ficticio).
        for (int j = 0; j < 2; j++) {
            double suma = 0;
            for (int i = 0; i < 3; i++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(10.0, suma, DELTA);
        }
    }
}
