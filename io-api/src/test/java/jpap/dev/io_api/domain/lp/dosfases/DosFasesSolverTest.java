package jpap.dev.io_api.domain.lp.dosfases;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DosFasesSolverTest {

    private static final double DELTA = 1e-5;
    private final DosFasesSolver solver = new DosFasesSolver();

    /**
     * MAX 3x1+2x2
     * x1+x2  <= 4  (LEQ)
     * x1+3x2 >= 6  (GEQ)
     * Mismo resultado que GranM: x1=3, x2=1, z=11
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
    }

    /**
     * MIN x1+2x2
     * x1+x2  >= 4  (GEQ)
     * 2x1+x2 >= 6  (GEQ)
     * Óptimo: x1=4, x2=0, z=4
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
     * Óptimo: x1=0, x2=4, z=12
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
     * MAX x1+x2; x1+x2<=3; x1+x2>=5
     * La Fase 1 debe detectar w* > 0.
     */
    @Test
    void infactible_fase1_detecta_w_positivo() {
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

        // Debe haber un paso que mencione infactibilidad de Fase 1
        boolean tieneStepInfactible = resultado.steps().stream()
                .anyMatch(s -> s.titulo().contains("infactible") || s.titulo().contains("Infactible"));
        assertTrue(tieneStepInfactible, "Debe haber un paso que indique la infactibilidad en Fase 1");
    }

    /**
     * Los pasos deben contener pasos de Fase 1 y pasos de Fase 2,
     * y la numeración debe ser continua y creciente.
     */
    @Test
    void pasos_incluyen_fase1_y_fase2_en_orden() {
        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 2.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 4.0),
                        new Restriccion(List.of(1.0, 3.0), TipoRestriccion.GEQ, 6.0)
                )
        );

        SolveResult<SolucionLP> resultado = solver.resolver(modelo);
        List<SolveStep> pasos = resultado.steps();

        // Al menos: paso inicial Fase 1, "completada", paso inicial Fase 2, solución
        assertTrue(pasos.size() >= 4, "Debe haber al menos 4 pasos (Fase 1 inicio, completada, Fase 2 inicio, solución)");

        // Verificar que existen pasos de Fase 1 y Fase 2
        boolean tieneFase1 = pasos.stream().anyMatch(s -> s.titulo().contains("Fase 1"));
        boolean tieneFase2 = pasos.stream().anyMatch(s -> s.titulo().contains("Fase 2"));
        assertTrue(tieneFase1, "Debe haber al menos un paso con 'Fase 1' en el título");
        assertTrue(tieneFase2, "Debe haber al menos un paso con 'Fase 2' en el título");

        // El paso "Fase 1 completada" debe aparecer antes del primer paso de Fase 2
        int idxFase1Completada = -1, idxPrimerFase2 = -1;
        for (int i = 0; i < pasos.size(); i++) {
            String titulo = pasos.get(i).titulo();
            if (titulo.contains("completada") && idxFase1Completada < 0) idxFase1Completada = i;
            if (titulo.contains("Fase 2") && !titulo.contains("completada") && idxPrimerFase2 < 0)
                idxPrimerFase2 = i;
        }
        assertTrue(idxFase1Completada >= 0, "Debe existir el paso 'Fase 1 completada'");
        assertTrue(idxPrimerFase2 >= 0, "Debe existir al menos un paso de Fase 2");
        assertTrue(idxFase1Completada < idxPrimerFase2,
                "El paso 'Fase 1 completada' debe preceder al primer paso de Fase 2");

        // Numeración continua y creciente
        for (int i = 1; i < pasos.size(); i++) {
            assertEquals(i, pasos.get(i).numero(),
                    "El número de paso " + i + " debe ser " + i);
        }
    }
}
