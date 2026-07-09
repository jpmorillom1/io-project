package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.produccion.PlanificacionProduccionSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanificacionProduccionSolverTest {

    private final PlanificacionProduccionSolver solver = new PlanificacionProduccionSolver();

    private ModeloDinamico modelo(List<Integer> demandas, Integer capacidadProduccion) {
        return ModeloDinamico.builder()
                .metodo(MetodoDinamico.PLANIFICACION_PRODUCCION)
                .demandas(demandas)
                .costoPreparacion(3.0)
                .costoUnitarioProduccion(1.0)
                .costoMantener(1.0)
                .capacidadProduccion(capacidadProduccion)
                .build();
    }

    /**
     * Demandas 3, 2, 4; preparar cuesta 3, producir 1 por unidad, mantener 1 por unidad-periodo.
     * El costo de produccion (9) es fijo, asi que solo compiten preparaciones y almacenamiento:
     *   producir 3-2-4 -> 3 preparaciones, 0 almacen  = 9 + 9      = 18
     *   producir 5-0-4 -> 2 preparaciones, 2 en stock = 6 + 9 + 2  = 17  (optimo)
     *   producir 3-6-0 -> 2 preparaciones, 4 en stock = 6 + 9 + 4  = 19
     *   producir 9-0-0 -> 1 preparacion, 10 en stock  = 3 + 9 + 10 = 22
     */
    @Test
    void plan_optimo_agrupa_los_dos_primeros_periodos() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(List.of(3, 2, 4), null));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(17.0, res.solution().valorOptimo(), 1e-9);

        List<DecisionOptima> politica = res.solution().politicaOptima();
        assertEquals(3, politica.size());
        assertEquals("x = 5", politica.get(0).decision());
        assertEquals("x = 0", politica.get(1).decision());
        assertEquals("x = 4", politica.get(2).decision());
        assertEquals("i = 2", politica.get(0).estadoSalida());
    }

    /** La capacidad de produccion no alcanza a cubrir la demanda del primer periodo: INFACTIBLE. */
    @Test
    void capacidad_insuficiente_devuelve_infactible() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(List.of(3, 2, 4), 2));

        assertEquals(SolveStatus.INFACTIBLE, res.status());
        assertNull(res.solution());
        assertEquals("Plan de producción infactible", res.steps().get(res.steps().size() - 1).titulo());
    }

    /** Un inventario inicial que ya cubre el primer periodo evita esa preparacion. */
    @Test
    void inventario_inicial_ahorra_la_primera_preparacion() {
        SolveResult<SolucionDinamica> res = solver.resolver(ModeloDinamico.builder()
                .metodo(MetodoDinamico.PLANIFICACION_PRODUCCION)
                .demandas(List.of(3, 2))
                .costoPreparacion(3.0)
                .costoUnitarioProduccion(1.0)
                .costoMantener(1.0)
                .inventarioInicial(3)
                .build());

        // Periodo 1: no producir (i=3, d=3, j=0, costo 0). Periodo 2: producir 2 -> 3 + 2 = 5.
        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(5.0, res.solution().valorOptimo(), 1e-9);
        assertEquals("x = 0", res.solution().politicaOptima().get(0).decision());
    }

    @Test
    void entrada_malformada_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(List.of(), null)));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(ModeloDinamico.builder()
                .metodo(MetodoDinamico.PLANIFICACION_PRODUCCION)
                .demandas(List.of(3, 2))
                .costoPreparacion(3.0)
                .costoUnitarioProduccion(1.0)
                .build()));   // falta costoMantener
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(ModeloDinamico.builder()
                .metodo(MetodoDinamico.PLANIFICACION_PRODUCCION)
                .sentido(SentidoOptimizacion.MAXIMIZAR)   // este modelo minimiza costo
                .demandas(List.of(3))
                .costoPreparacion(3.0)
                .costoUnitarioProduccion(1.0)
                .costoMantener(1.0)
                .build()));
    }
}
