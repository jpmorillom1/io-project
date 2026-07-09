package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.asignacion.AsignacionRecursosSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsignacionRecursosSolverTest {

    private final AsignacionRecursosSolver solver = new AsignacionRecursosSolver();

    private ModeloDinamico modelo(int recurso, List<ActividadRecurso> actividades) {
        return ModeloDinamico.builder()
                .metodo(MetodoDinamico.ASIGNACION_RECURSOS)
                .recursoTotal(recurso)
                .actividades(actividades)
                .build();
    }

    /**
     * 2 unidades de recurso entre 2 actividades. A rinde [0,4,6] y B rinde [0,3,8].
     * f_2(2)=8, y en la etapa 1: x=0 -> 0+8=8, x=1 -> 4+3=7, x=2 -> 6+0=6.
     * Optimo: darle las 2 unidades a B, retorno total 8.
     */
    @Test
    void asignacion_reparte_todo_el_recurso_a_la_actividad_mas_rentable() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(2, List.of(
                new ActividadRecurso("A", List.of(0.0, 4.0, 6.0)),
                new ActividadRecurso("B", List.of(0.0, 3.0, 8.0)))));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(8.0, res.solution().valorOptimo(), 1e-9);

        List<DecisionOptima> politica = res.solution().politicaOptima();
        assertEquals(2, politica.size());
        assertEquals("x = 0", politica.get(0).decision());
        assertEquals("x = 2", politica.get(1).decision());
    }

    /** Las tablas se emiten en el orden de la recursion hacia atras: primero la ultima etapa. */
    @Test
    void las_tablas_van_de_la_ultima_etapa_a_la_primera() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(2, List.of(
                new ActividadRecurso("A", List.of(0.0, 4.0, 6.0)),
                new ActividadRecurso("B", List.of(0.0, 3.0, 8.0)))));

        List<TablaEtapa> tablas = res.solution().tablas();
        assertEquals(2, tablas.size());
        assertEquals(2, tablas.get(0).etapa());
        assertEquals(1, tablas.get(1).etapa());
        // Una fila por estado s = 0, 1, 2.
        assertEquals(3, tablas.get(0).filas().size());
        assertFalse(res.steps().isEmpty());
        assertEquals("Formulación del modelo", res.steps().get(0).titulo());
    }

    /** Cada actividad debe traer un retorno por cada asignacion posible (recursoTotal + 1). */
    @Test
    void retornos_de_tamano_incorrecto_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(2, List.of(
                new ActividadRecurso("A", List.of(0.0, 4.0))))));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(2, List.of())));
    }
}
