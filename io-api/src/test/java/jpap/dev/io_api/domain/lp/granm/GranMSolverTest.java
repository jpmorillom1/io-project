package jpap.dev.io_api.domain.lp.granm;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GranMSolverTest {

    private static final double DELTA = 1e-5;
    private final GranMSolver solver = new GranMSolver();

    /**
     * MAX 3x1+2x2
     * x1+x2  <= 4  (LEQ)
     * x1+3x2 >= 6  (GEQ)
     * Óptimo verificado: x1=3, x2=1, z=11
     * (x1+x2=4 activa, x1+3x2=6 activa)
     */
    @Test
    void maximizacion_mixta_leq_geq() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 2.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 4.0),
                        new Restriccion(List.of(1.0, 3.0), TipoRestriccion.GEQ, 6.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, resultado.status());
        assertNotNull(resultado.solution());
        assertEquals(11.0, resultado.solution().valorOptimo(), DELTA);
        assertEquals(3.0,  resultado.solution().valores().get("x1"), DELTA);
        assertEquals(1.0,  resultado.solution().valores().get("x2"), DELTA);

        // holguras, precios sombra y rangos deben estar presentes
        assertNotNull(resultado.solution().holguras());
        assertFalse(resultado.solution().holguras().isEmpty());
        assertNotNull(resultado.solution().preciosSombra());
        assertEquals(2, resultado.solution().preciosSombra().size());
        assertNotNull(resultado.solution().rangosSensibilidad());
        assertEquals(2, resultado.solution().rangosSensibilidad().coeficientesObjetivo().size());
        assertEquals(2, resultado.solution().rangosSensibilidad().rhs().size());
    }

    /**
     * MIN x1+2x2
     * x1+x2  >= 4  (GEQ)
     * 2x1+x2 >= 6  (GEQ)
     * Óptimo verificado: x1=4, x2=0, z=4
     */
    @Test
    void minimizacion_todo_geq() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(1.0, 2.0), TipoObjetivo.MINIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.GEQ, 4.0),
                        new Restriccion(List.of(2.0, 1.0), TipoRestriccion.GEQ, 6.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, resultado.status());
        assertNotNull(resultado.solution());
        assertEquals(4.0, resultado.solution().valorOptimo(), DELTA);
        assertEquals(4.0, resultado.solution().valores().get("x1"), DELTA);
        assertEquals(0.0, resultado.solution().valores().get("x2"), DELTA);
    }

    /**
     * MAX 2x1+3x2
     * x1+x2  =  4  (EQ)
     * x1-x2  <= 2  (LEQ)
     * Óptimo verificado: x2=4-x1; z=12-x1 → MAX en x1=0 → x1=0, x2=4, z=12
     */
    @Test
    void maximizacion_con_restriccion_eq() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(2.0, 3.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.EQ, 4.0),
                        new Restriccion(List.of(1.0, -1.0), TipoRestriccion.LEQ, 2.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.OPTIMO, resultado.status());
        assertNotNull(resultado.solution());
        assertEquals(12.0, resultado.solution().valorOptimo(), DELTA);
        assertEquals(0.0,  resultado.solution().valores().get("x1"), DELTA);
        assertEquals(4.0,  resultado.solution().valores().get("x2"), DELTA);
    }

    /**
     * INFACTIBLE: restricciones contradictoras
     * MAX x1+x2
     * x1+x2 <= 3
     * x1+x2 >= 5
     */
    @Test
    void infactible_restricciones_contradictorias() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(1.0, 1.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 3.0),
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.GEQ, 5.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, resultado.status());
        assertNull(resultado.solution());
        assertFalse(resultado.steps().isEmpty());
    }

    /**
     * Los pasos deben incluir el tableau inicial con todos los encabezados
     * y al menos un paso con la clave "varEntra".
     */
    @Test
    void pasos_incluyen_tableau_inicial_y_varEntra() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 2.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 4.0),
                        new Restriccion(List.of(1.0, 3.0), TipoRestriccion.GEQ, 6.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);

        assertTrue(resultado.steps().size() >= 2);
        assertEquals(0, resultado.steps().get(0).numero());
        assertNotNull(resultado.steps().get(0).datos().get("tableau"));
        assertNotNull(resultado.steps().get(0).datos().get("base"));

        // Debe haber al menos un paso con información de pivote
        boolean tieneVarEntra = resultado.steps().stream()
                .anyMatch(s -> s.datos().containsKey("varEntra"));
        assertTrue(tieneVarEntra, "Debe haber al menos un paso con 'varEntra'");

        // Los encabezados deben incluir variables artificiales
        @SuppressWarnings("unchecked")
        List<String> encabezados = (List<String>) resultado.steps().get(0).datos().get("encabezados");
        assertNotNull(encabezados);
        assertTrue(encabezados.stream().anyMatch(h -> h.startsWith("a")),
                "Los encabezados deben incluir al menos una variable artificial");
    }
}
