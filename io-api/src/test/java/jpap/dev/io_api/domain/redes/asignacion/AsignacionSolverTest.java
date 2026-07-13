package jpap.dev.io_api.domain.redes.asignacion;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Matriz 3×3 con óptimo conocido (enumeradas las 6 permutaciones a mano):
 *     T1  T2  T3
 * A1 [ 9   2   7 ]
 * A2 [ 6   4   3 ]
 * A3 [ 5   8   1 ]
 * Óptimo: A1→T2 (2) + A2→T1 (6) + A3→T3 (1) = 9.
 */
class AsignacionSolverTest {

    private static final double DELTA = 1e-6;

    private final AsignacionSolver solver = new AsignacionSolver();

    private ModeloRed problema() {
        return new ModeloRed(null, null, true, MetodoRed.ASIGNACION, null, null,
                List.of("A1", "A2", "A3"),
                List.of("T1", "T2", "T3"),
                List.of(
                        List.of(9.0, 2.0, 7.0),
                        List.of(6.0, 4.0, 3.0),
                        List.of(5.0, 8.0, 1.0)));
    }

    @Test
    void encuentraLaAsignacionOptimaConocida() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(9.0, r.solution().costoTotal(), DELTA);
        assertEquals(9.0, r.solution().valorObjetivo(), DELTA);
        assertEquals("T2", r.solution().asignacion().get("A1"));
        assertEquals("T1", r.solution().asignacion().get("A2"));
        assertEquals("T3", r.solution().asignacion().get("A3"));
        assertEquals(3, r.solution().aristasSolucion().size());
    }

    @Test
    void balanceaConFicticioCuandoHayMasTareasQueAgentes() {
        ModeloRed modelo = new ModeloRed(null, null, true, MetodoRed.ASIGNACION, null, null,
                List.of("A1", "A2"),
                List.of("T1", "T2", "T3"),
                List.of(
                        List.of(10.0, 1.0, 10.0),
                        List.of(2.0, 10.0, 10.0)));

        SolveResult<SolucionRed> r = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(3.0, r.solution().costoTotal(), DELTA);
        assertEquals("T2", r.solution().asignacion().get("A1"));
        assertEquals("T1", r.solution().asignacion().get("A2"));
        // El agente ficticio (que absorbe T3) no aparece en la asignación
        assertEquals(2, r.solution().asignacion().size());
        assertFalse(r.solution().asignacion().containsKey("Ficticio"));
        // El paso 0 explica el balanceo
        assertTrue(r.steps().get(0).descripcion().contains("Ficticio"));
    }

    @Test
    void dimensionesInconsistentesLanzanExcepcion() {
        ModeloRed modelo = new ModeloRed(null, null, true, MetodoRed.ASIGNACION, null, null,
                List.of("A1", "A2"),
                List.of("T1", "T2"),
                List.of(List.of(1.0, 2.0)));   // falta la fila de A2

        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo));
    }

    @Test
    void losPasosLlevanLaRedBipartitaParaLaUi() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals("REDES", r.steps().get(0).datos().get("tipo"));
        assertEquals("ASIGNACION", r.steps().get(0).datos().get("metodo"));
        assertNotNull(r.steps().get(0).datos().get("aristas"));
    }
}
