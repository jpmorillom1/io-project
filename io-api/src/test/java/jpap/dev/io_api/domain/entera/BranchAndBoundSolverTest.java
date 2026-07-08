package jpap.dev.io_api.domain.entera;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.entera.branchandbound.BranchAndBoundSolver;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchAndBoundSolverTest {

    private final BranchAndBoundSolver solver = new BranchAndBoundSolver();

    private Restriccion leq(List<Double> coefs, double rhs) {
        return new Restriccion(coefs, TipoRestriccion.LEQ, rhs);
    }

    private Restriccion geq(List<Double> coefs, double rhs) {
        return new Restriccion(coefs, TipoRestriccion.GEQ, rhs);
    }

    /**
     * Ejemplo clásico: MAX 5x1 + 4x2, s.a. 6x1+4x2 ≤ 24, x1+2x2 ≤ 6, x1,x2 enteras.
     * Relajación LP: (3, 1.5) con Z=21. Óptimo entero (verificado por enumeración): (4,0) con Z=20.
     */
    @Test
    void resuelve_optimo_entero_conocido() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                        List.of(leq(List.of(6.0, 4.0), 24), leq(List.of(1.0, 2.0), 6))),
                List.of(TipoVariable.ENTERA, TipoVariable.ENTERA));

        SolveResult<SolucionEntera> res = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(20.0, res.solution().valorOptimo(), 1e-6);
        assertEquals(4.0, res.solution().valores().get("x1"), 1e-6);
        assertEquals(0.0, res.solution().valores().get("x2"), 1e-6);
        assertEquals(21.0, res.solution().valorRelajacion(), 1e-6);
        assertTrue(res.solution().nodosExplorados() >= 1);
        // Debe haber al menos una ramificación en el árbol.
        assertTrue(res.steps().stream().anyMatch(s -> "RAMIFICA".equals(s.datos().get("accion"))),
                "el árbol debe contener al menos un nodo de ramificación");
    }

    /**
     * Selección de proyectos (mochila binaria): MAX 5x1+4x2+3x3, s.a. 2x1+3x2+4x3 ≤ 6, binarias.
     * Óptimo (enumeración): elegir x1 y x2 (peso 5 ≤ 6), Z=9.
     */
    @Test
    void resuelve_problema_binario_de_seleccion() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2", "x3"),
                        new FuncionObjetivo(List.of(5.0, 4.0, 3.0), TipoObjetivo.MAXIMIZAR),
                        List.of(leq(List.of(2.0, 3.0, 4.0), 6))),
                List.of(TipoVariable.BINARIA, TipoVariable.BINARIA, TipoVariable.BINARIA));

        SolveResult<SolucionEntera> res = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(9.0, res.solution().valorOptimo(), 1e-6);
        assertEquals(1.0, res.solution().valores().get("x1"), 1e-6);
        assertEquals(1.0, res.solution().valores().get("x2"), 1e-6);
        assertEquals(0.0, res.solution().valores().get("x3"), 1e-6);
        // Ninguna binaria debe exceder 1 (cota implícita x ≤ 1 aplicada por el solver).
        res.solution().valores().values().forEach(v -> assertTrue(v <= 1.0 + 1e-6));
    }

    /**
     * Cuando la relajación LP ya es entera, no hace falta ramificar: se resuelve en la raíz.
     * MAX x1+x2, s.a. x1 ≤ 3, x2 ≤ 2, enteras → (3,2) Z=5, un solo nodo.
     */
    @Test
    void relajacion_ya_entera_no_ramifica() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(1.0, 1.0), TipoObjetivo.MAXIMIZAR),
                        List.of(leq(List.of(1.0, 0.0), 3), leq(List.of(0.0, 1.0), 2))),
                List.of(TipoVariable.ENTERA, TipoVariable.ENTERA));

        SolveResult<SolucionEntera> res = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(5.0, res.solution().valorOptimo(), 1e-6);
        assertEquals(1, res.solution().nodosExplorados(), "solo la raíz debe explorarse");
    }

    /**
     * LP factible pero SIN solución entera: 2x1 ≤ 1 y 2x1 ≥ 1 fuerzan x1 = 0.5, que no es entero.
     * Ambas ramas (x1 ≤ 0 y x1 ≥ 1) son infactibles → INFACTIBLE.
     */
    @Test
    void infactible_en_enteros_devuelve_infactible() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1"),
                        new FuncionObjetivo(List.of(1.0), TipoObjetivo.MAXIMIZAR),
                        List.of(leq(List.of(2.0), 1), geq(List.of(2.0), 1))),
                List.of(TipoVariable.ENTERA));

        SolveResult<SolucionEntera> res = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, res.status());
        assertNull(res.solution());
    }

    @Test
    void tipos_variable_desalineados_lanzan_excepcion() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(1.0, 1.0), TipoObjetivo.MAXIMIZAR),
                        List.of(leq(List.of(1.0, 1.0), 3))),
                List.of(TipoVariable.ENTERA));   // falta un tipo

        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo));
    }
}
