package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.mochila.MochilaSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MochilaSolverTest {

    private final MochilaSolver solver = new MochilaSolver();

    private ModeloDinamico modelo(int capacidad, List<ArticuloMochila> articulos) {
        return ModeloDinamico.builder()
                .metodo(MetodoDinamico.MOCHILA)
                .capacidad(capacidad)
                .articulos(articulos)
                .build();
    }

    /**
     * Mochila 0/1 con capacidad 5: A(peso 2, valor 3), B(peso 3, valor 4), C(peso 4, valor 5).
     * Llevar A+B pesa 5 y vale 7; C solo vale 5. Optimo = 7.
     */
    @Test
    void mochila_binaria_elige_la_mejor_combinacion_no_el_mejor_articulo() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(5, List.of(
                new ArticuloMochila("A", 2, 3.0, null),
                new ArticuloMochila("B", 3, 4.0, null),
                new ArticuloMochila("C", 4, 5.0, null))));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(7.0, res.solution().valorOptimo(), 1e-9);

        List<DecisionOptima> politica = res.solution().politicaOptima();
        assertEquals("x = 1", politica.get(0).decision());   // A
        assertEquals("x = 1", politica.get(1).decision());   // B
        assertEquals("x = 0", politica.get(2).decision());   // C
        assertTrue(res.solution().interpretacionPolitica().contains("A"));
    }

    /** Con unidadesMaximas > 1 se pueden llevar varias copias del mismo articulo. */
    @Test
    void mochila_con_varias_unidades_del_mismo_articulo() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(6, List.of(
                new ArticuloMochila("A", 2, 3.0, 3))));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(9.0, res.solution().valorOptimo(), 1e-9);
        assertEquals("x = 3", res.solution().politicaOptima().get(0).decision());
    }

    /** Nada cabe: la mochila queda vacia, pero sigue siendo un OPTIMO (no una excepcion). */
    @Test
    void mochila_sin_capacidad_suficiente_devuelve_optimo_vacio() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(1, List.of(
                new ArticuloMochila("A", 2, 3.0, null))));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(0.0, res.solution().valorOptimo(), 1e-9);
    }

    @Test
    void entrada_malformada_lanza() {
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(5, List.of())));
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(5, List.of(
                new ArticuloMochila("A", 0, 3.0, null)))));   // peso no positivo
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(ModeloDinamico.builder()
                .metodo(MetodoDinamico.MOCHILA)
                .sentido(SentidoOptimizacion.MINIMIZAR)       // la mochila siempre maximiza
                .capacidad(5)
                .articulos(List.of(new ArticuloMochila("A", 2, 3.0, null)))
                .build()));
    }
}
