package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.reemplazo.ReemplazoEquiposSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReemplazoEquiposSolverTest {

    private final ReemplazoEquiposSolver solver = new ReemplazoEquiposSolver();

    /** edad 0: ingreso 20, operacion 2, rescate 8 · edad 1: 18, 4, 6 · edad 2: 15, 8, 3. */
    private static List<DatosEdadEquipo> tabla() {
        return List.of(
                new DatosEdadEquipo(0, 20, 2, 8),
                new DatosEdadEquipo(1, 18, 4, 6),
                new DatosEdadEquipo(2, 15, 8, 3));
    }

    private ModeloDinamico modelo(int horizonte, Integer edadInicial) {
        return ModeloDinamico.builder()
                .metodo(MetodoDinamico.REEMPLAZO_EQUIPOS)
                .horizonteAnios(horizonte)
                .edadMaxima(2)
                .edadInicial(edadInicial)
                .costoCompra(10.0)
                .tablaEdades(tabla())
                .build();
    }

    /**
     * Horizonte 2, equipo nuevo, precio de uno nuevo 10. Frontera f_3(e) = rescate(e).
     * Ano 2 con edad 1: conservar = 18-4+f_3(2)=14+3=17; reemplazar = 6-10+20-2+f_3(1)=14+6=20 -> REEMPLAZAR.
     * Ano 1 con edad 0: conservar = 20-2+f_2(1)=18+20=38; reemplazar = 8-10+18+20=36 -> CONSERVAR.
     * Ingreso neto maximo: 38.
     */
    @Test
    void reemplazo_conserva_el_primer_ano_y_reemplaza_el_segundo() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(2, 0));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(38.0, res.solution().valorOptimo(), 1e-9);

        List<DecisionOptima> politica = res.solution().politicaOptima();
        assertEquals(2, politica.size());
        assertEquals("CONSERVAR", politica.get(0).decision());
        assertEquals("REEMPLAZAR", politica.get(1).decision());
        assertEquals("edad = 1", politica.get(1).estadoEntrada());
    }

    /**
     * A la edad maxima CONSERVAR no es admisible: la unica decision es REEMPLAZAR.
     * Ano 1 con edad 2: 3-10+20-2+f_2(1)=11+6=17.
     */
    @Test
    void a_la_edad_maxima_solo_cabe_reemplazar() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(1, 2));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(17.0, res.solution().valorOptimo(), 1e-9);
        assertEquals("REEMPLAZAR", res.solution().politicaOptima().get(0).decision());

        FilaEtapa unicaFila = res.solution().tablas().get(0).filas().get(0);
        assertEquals(1, unicaFila.evaluaciones().size());   // solo la evaluacion de REEMPLAZAR
        assertEquals("REEMPLAZAR", unicaFila.evaluaciones().get(0).decision());
    }

    @Test
    void entrada_malformada_lanza() {
        // tablaEdades no cubre todas las edades 0..edadMaxima
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(ModeloDinamico.builder()
                .metodo(MetodoDinamico.REEMPLAZO_EQUIPOS)
                .horizonteAnios(2).edadMaxima(2).costoCompra(10.0)
                .tablaEdades(List.of(new DatosEdadEquipo(0, 20, 2, 8)))
                .build()));
        // edad inicial fuera de rango
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(2, 5)));
        // horizonte invalido
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(0, 0)));
    }
}
