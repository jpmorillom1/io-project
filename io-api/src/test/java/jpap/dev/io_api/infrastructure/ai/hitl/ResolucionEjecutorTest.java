package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.application.lp.GranMService;
import jpap.dev.io_api.application.lp.SimplexService;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El resumen que el ejecutor entrega al tutor debe traer el análisis de sensibilidad
 * con los números REALES del solver: es lo único que impide que el tutor los invente.
 *
 * Sin Spring: los use cases son delegadores sin estado, así que se construyen con new
 * y los que no intervienen van a null.
 */
class ResolucionEjecutorTest {

    private final ResolucionEjecutor ejecutor = new ResolucionEjecutor(
            new SimplexService(), new GranMService(),
            null, null, null, null, null, null, null);

    /** MAX 5x1+4x2 s.a 6x1+4x2<=24, x1+2x2<=6 — óptimo (3,1.5), Z=21. Ambos recursos agotados. */
    private ModeloLP lpClasico() {
        return new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0),
                        new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6.0)
                ));
    }

    @Test
    void simplex_incluye_valor_marginal_y_rangos_de_cada_restriccion() {
        String resumen = ejecutor.ejecutar(lpClasico(), MetodoResolucion.SIMPLEX).resumenParaTutor();

        assertTrue(resumen.contains("ANÁLISIS DE SENSIBILIDAD"));
        assertTrue(resumen.contains("R1: 6.0·x1 + 4.0·x2 <= 24.0"));
        assertTrue(resumen.contains("R2: 1.0·x1 + 2.0·x2 <= 6.0"));

        // Ambas restricciones están activas en el óptimo (3, 1.5): ninguna tiene sobrante.
        assertTrue(resumen.contains("sin sobrante (recurso agotado"));
        assertFalse(resumen.contains("sobran "));

        assertTrue(resumen.contains("1 unidad más del lado derecho cambia el óptimo en"));
        assertTrue(resumen.contains("el plan óptimo no cambia mientras esté entre"));

        // El bloque va ANTES del detalle de iteraciones, para sobrevivir a un truncado.
        assertTrue(resumen.indexOf("ANÁLISIS DE SENSIBILIDAD") < resumen.indexOf("DETALLE DE ITERACIONES"));
    }

    /**
     * La trampa del mapeo: con [<=, =, >=] el solver NO crea holgura para la EQ, así que
     * "s2" es el sobrante de R3, no el de R2. Unirlos por número daría datos falsos.
     */
    @Test
    void con_restriccion_de_igualdad_el_sobrante_no_se_corre_de_restriccion() {
        // MAX 5x1+4x2 s.a 6x1+4x2<=24, x1=2, x2>=1  → óptimo (2, 3): R1 sobra 24-12-12=0
        ModeloLP mixto = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0),
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.EQ, 2.0),
                        new Restriccion(List.of(0.0, 1.0), TipoRestriccion.GEQ, 1.0)
                ));

        String resumen = ejecutor.ejecutar(mixto, MetodoResolucion.GRAN_M).resumenParaTutor();

        // La EQ nunca se describe con un sobrante numérico...
        assertTrue(resumen.contains("R2: 1.0·x1 + 0.0·x2 = 2.0"));
        assertTrue(resumen.contains("restricción de igualdad: se cumple exacta, no tiene sobrante"));

        // ...y la GEQ (que consume el índice de holgura s2) se describe como excedente sobre
        // el mínimo, no como holgura de un recurso: x2=3 supera en 2 el mínimo exigido de 1.
        assertTrue(resumen.contains("se supera el mínimo exigido por 2.0 unidades"));
    }

    @Test
    void un_rango_no_acotado_se_lee_y_nunca_sale_como_Infinity() {
        // La tercera restricción es redundante (el óptimo es (3, 1.5)): le sobra capacidad, así que
        // su lado derecho puede crecer sin límite sin que el óptimo cambie.
        ModeloLP conRedundante = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0),
                        new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6.0),
                        new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 100.0)
                ));

        String resumen = ejecutor.ejecutar(conRedundante, MetodoResolucion.SIMPLEX).resumenParaTutor();

        assertFalse(resumen.contains("Infinity"), "Jackson y el LLM leerían basura; el infinito nunca sale del solver");
        assertFalse(resumen.contains("NaN"));
        assertTrue(resumen.contains("sin límite"), "un rango no acotado debe leerse, no imprimirse como ∞");
        assertTrue(resumen.contains("sobran 95.5 unidades (no limita la solución)"));
    }

    @Test
    void un_problema_infactible_no_emite_bloque_de_sensibilidad() {
        // x1 >= 4 junto con x1 <= 2: no hay región factible.
        ModeloLP infactible = new ModeloLP(
                List.of("x1"),
                new FuncionObjetivo(List.of(1.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0), TipoRestriccion.LEQ, 2.0),
                        new Restriccion(List.of(1.0), TipoRestriccion.GEQ, 4.0)
                ));

        String resumen = ejecutor.ejecutar(infactible, MetodoResolucion.GRAN_M).resumenParaTutor();

        assertTrue(resumen.contains("INFACTIBLE"));
        assertFalse(resumen.contains("ANÁLISIS DE SENSIBILIDAD"));
    }
}
