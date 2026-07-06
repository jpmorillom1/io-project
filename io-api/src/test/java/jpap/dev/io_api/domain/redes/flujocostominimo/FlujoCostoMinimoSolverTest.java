package jpap.dev.io_api.domain.redes.flujocostominimo;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Red que obliga al SSP a re-rutear por un arco inverso (verificada a mano):
 *   S→A(cap 1, costo 1), S→B(1, 2), A→B(1, 0), A→T(1, 3), B→T(1, 1)
 *
 * 1er camino (el más barato): S→A→B→T con costo unitario 2 — satura A→B y B→T.
 * 2º camino: S→B→A→T usando el INVERSO de A→B (costo 0) — cancela el flujo de A→B.
 * Resultado: flujo máximo 2 con costo total mínimo 7 (equivale a S→A→T + S→B→T).
 */
class FlujoCostoMinimoSolverTest {

    private static final double DELTA = 1e-6;

    private final FlujoCostoMinimoSolver solver = new FlujoCostoMinimoSolver();

    private Arista arco(String origen, String destino, double capacidad, double costo) {
        return new Arista(origen, destino, null, capacidad, costo);
    }

    private ModeloRed problema() {
        return new ModeloRed(
                List.of("S", "A", "B", "T"),
                List.of(
                        arco("S", "A", 1, 1),
                        arco("S", "B", 1, 2),
                        arco("A", "B", 1, 0),
                        arco("A", "T", 1, 3),
                        arco("B", "T", 1, 1)),
                true, MetodoRed.FLUJO_COSTO_MINIMO, "S", "T", null, null, null);
    }

    @Test
    void encuentraElCostoMinimoReRuteandoPorElArcoInverso() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(2.0, r.solution().flujoTotal(), DELTA);
        assertEquals(7.0, r.solution().costoTotal(), DELTA);
        assertEquals(7.0, r.solution().valorObjetivo(), DELTA);

        // El flujo por A→B quedó cancelado por el re-ruteo: no aparece entre los arcos usados
        assertFalse(r.solution().flujoPorArco().containsKey("A->B"));
        assertEquals(1.0, r.solution().flujoPorArco().get("A->T"), DELTA);
        assertEquals(1.0, r.solution().flujoPorArco().get("B->T"), DELTA);

        // El segundo camino aumentante debe haber usado un arco inverso
        assertTrue(r.steps().stream().anyMatch(s -> s.descripcion().contains("inverso")),
                "el SSP debe registrar el re-ruteo por el arco inverso");
    }

    @Test
    void sinCaminoFuenteSumideroEsInfactible() {
        ModeloRed modelo = new ModeloRed(
                List.of("S", "T"),
                List.of(arco("T", "S", 5, 1)),   // el único arco apunta al revés
                true, MetodoRed.FLUJO_COSTO_MINIMO, "S", "T", null, null, null);

        SolveResult<SolucionRed> r = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, r.status());
        assertNull(r.solution());
    }

    @Test
    void losPasosRegistranCostoYCuelloDeBotella() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals("REDES", r.steps().get(0).datos().get("tipo"));
        assertTrue(r.steps().stream().anyMatch(s ->
                        s.datos().containsKey("costoUnitario") && s.datos().containsKey("cuelloBotella")),
                "cada camino aumentante debe registrar costo unitario y cuello de botella");
    }
}
