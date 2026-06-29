package jpap.dev.io_api.domain.lp.simplex;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.RangoCoeficiente;
import jpap.dev.io_api.domain.lp.RangoRHS;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimplexSolverTest {

    private static final double DELTA = 1e-5;
    private final SimplexSolver solver = new SimplexSolver();

    /**
     * Problema clásico:
     *   MAX z = 3x1 + 5x2
     *   s.a.  x1       <= 4
     *         2x2      <= 12
     *         3x1 + 5x2 <= 25
     *   Solución óptima: x1=0, x2=5, z=25  — o bien x1=5/3, x2=... depende del ejemplo
     *
     * Usamos el clásico de Hillier & Lieberman:
     *   MAX z = 3x1 + 5x2
     *         x1       <= 4
     *         2x2      <= 12
     *         3x1+5x2  <= 18
     *   Óptimo: x1=2, x2=6/... nope, revisemos.
     *
     * Ejemplo verificable:
     *   MAX z = 5x1 + 4x2
     *         6x1 + 4x2 <= 24
     *         x1  + 2x2 <= 6
     *   Óptimo: x1=3, x2=3/2=1.5 → z=5*3+4*1.5=15+6=21
     */
    @Test
    void maximizacion_simple() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0),
                        new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, resultado.status());
        assertNotNull(resultado.solution());
        assertEquals(21.0, resultado.solution().valorOptimo(), DELTA);
        assertEquals(3.0,  resultado.solution().valores().get("x1"), DELTA);
        assertEquals(1.5,  resultado.solution().valores().get("x2"), DELTA);
        assertFalse(resultado.steps().isEmpty());
    }

    /**
     * Minimización:
     *   MIN z = x1 + 2x2
     *         x1 + x2  <= 10
     *         x1       <= 6
     *         x2       <= 8
     *   Óptimo en origen: x1=0, x2=0, z=0
     */
    @Test
    void minimizacion_optimo_en_origen() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(1.0, 2.0), TipoObjetivo.MINIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 10.0),
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.LEQ, 6.0),
                        new Restriccion(List.of(0.0, 1.0), TipoRestriccion.LEQ, 8.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, resultado.status());
        assertEquals(0.0, resultado.solution().valorOptimo(), DELTA);
    }

    /**
     * Problema no acotado:
     *   MAX z = x1 + x2
     *         -x1 + x2 <= 1   (única restricción)
     *   x1, x2 >= 0 → z puede crecer sin límite
     */
    @Test
    void no_acotado() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(1.0, 1.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(-1.0, 1.0), TipoRestriccion.LEQ, 1.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.NO_ACOTADO, resultado.status());
        assertNull(resultado.solution());
    }

    @Test
    void valida_restriccion_no_leq() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1"),
                new FuncionObjetivo(List.of(1.0), TipoObjetivo.MAXIMIZAR),
                List.of(new Restriccion(List.of(1.0), TipoRestriccion.GEQ, 5.0))
        );
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo));
    }

    @Test
    void holguras_precios_sombra_y_rangos_sensibilidad() {
        // MAX 5x1+4x2, 6x1+4x2<=24, x1+2x2<=6 — ambas restricciones activas en el óptimo
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0),
                        new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6.0)
                )
        );

        SolucionLP sol = solver.resolver(modelo).solution();

        // holguras — ambas en 0 (restricciones activas)
        assertNotNull(sol.holguras());
        assertEquals(0.0, sol.holguras().get("s1"), DELTA);
        assertEquals(0.0, sol.holguras().get("s2"), DELTA);

        // precios sombra — y = c_B^T * B^{-1}
        assertNotNull(sol.preciosSombra());
        assertEquals(0.75, sol.preciosSombra().get("R1"), DELTA);
        assertEquals(0.50, sol.preciosSombra().get("R2"), DELTA);

        // rangos del objetivo
        assertNotNull(sol.rangosSensibilidad());
        RangoCoeficiente rx1 = sol.rangosSensibilidad().coeficientesObjetivo().get(0);
        RangoCoeficiente rx2 = sol.rangosSensibilidad().coeficientesObjetivo().get(1);

        assertEquals("x1", rx1.variable());
        assertEquals(4.0,  rx1.min(), DELTA);
        assertEquals(8.0,  rx1.max(), DELTA);

        assertEquals("x2", rx2.variable());
        assertEquals(-2.0,       rx2.min(), DELTA);
        assertEquals(4.666667,   rx2.max(), DELTA);

        // rangos del RHS
        RangoRHS r1 = sol.rangosSensibilidad().rhs().get(0);
        RangoRHS r2 = sol.rangosSensibilidad().rhs().get(1);

        assertEquals("R1", r1.restriccion());
        assertEquals(12.0, r1.min(), DELTA);
        assertEquals(36.0, r1.max(), DELTA);

        assertEquals("R2", r2.restriccion());
        assertEquals(4.0,  r2.min(), DELTA);
        assertEquals(12.0, r2.max(), DELTA);
    }

    @Test
    void holguras_no_nulas_cuando_restriccion_no_activa() {
        // MAX 3x1+5x2, x1<=4, 2x2<=12, 3x1+5x2<=25 — x1<=4 no estará activa en el óptimo
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 5.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.LEQ, 4.0),
                        new Restriccion(List.of(0.0, 2.0), TipoRestriccion.LEQ, 12.0),
                        new Restriccion(List.of(3.0, 5.0), TipoRestriccion.LEQ, 25.0)
                )
        );

        SolucionLP sol = solver.resolver(modelo).solution();

        assertNotNull(sol.holguras());
        // x2=5, luego 2x2=10 < 12 → s2 > 0
        assertTrue(sol.holguras().get("s2") > 0.0, "s2 debe ser positiva (restriccion no activa)");
        // precio sombra de restriccion no activa debe ser 0
        assertEquals(0.0, sol.preciosSombra().get("R2"), DELTA);
    }

    @Test
    void pasos_incluyen_tableau_inicial_y_final() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 5.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.LEQ, 4.0),
                        new Restriccion(List.of(0.0, 2.0), TipoRestriccion.LEQ, 12.0),
                        new Restriccion(List.of(3.0, 5.0), TipoRestriccion.LEQ, 25.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertTrue(resultado.steps().size() >= 2, "Debe haber al menos tableau inicial y solución final");
        assertEquals(0, resultado.steps().get(0).numero());
        assertNotNull(resultado.steps().get(0).datos().get("tableau"));
        assertNotNull(resultado.steps().get(0).datos().get("base"));
    }
}
