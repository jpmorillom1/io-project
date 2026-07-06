package jpap.dev.io_api.domain.redes.edmondskarp;

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
 * Red dirigida con flujo máximo conocido (corte {S} = 3 + 2 = 5, alcanzable):
 *   S→A(3), S→B(2), A→B(1), A→T(2), B→T(3)  →  flujo máximo = 5.
 */
class EdmondsKarpSolverTest {

    private static final double DELTA = 1e-6;

    private final EdmondsKarpSolver solver = new EdmondsKarpSolver();

    private Arista arco(String origen, String destino, double capacidad) {
        return new Arista(origen, destino, null, capacidad, null);
    }

    private ModeloRed problema() {
        return new ModeloRed(
                List.of("S", "A", "B", "T"),
                List.of(
                        arco("S", "A", 3),
                        arco("S", "B", 2),
                        arco("A", "B", 1),
                        arco("A", "T", 2),
                        arco("B", "T", 3)),
                true, MetodoRed.EDMONDS_KARP, "S", "T", null, null, null);
    }

    @Test
    void encuentraElFlujoMaximoConocido() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(5.0, r.solution().valorObjetivo(), DELTA);
        assertEquals(5.0, r.solution().flujoTotal(), DELTA);

        // Conservación en la fuente: todo el flujo sale de S
        double saleDeS = r.solution().flujoPorArco().getOrDefault("S->A", 0.0)
                + r.solution().flujoPorArco().getOrDefault("S->B", 0.0);
        assertEquals(5.0, saleDeS, DELTA);
    }

    @Test
    void sinCaminoFuenteSumideroEsInfactible() {
        ModeloRed modelo = new ModeloRed(
                List.of("S", "A", "T"),
                List.of(arco("S", "A", 4)),
                true, MetodoRed.EDMONDS_KARP, "S", "T", null, null, null);

        SolveResult<SolucionRed> r = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, r.status());
        assertNull(r.solution());
    }

    @Test
    void losPasosRegistranLosCaminosDeAumento() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals("REDES", r.steps().get(0).datos().get("tipo"));
        assertTrue(r.steps().stream().anyMatch(s -> s.datos().containsKey("cuelloBotella")),
                "cada camino de aumento debe registrar su cuello de botella");
    }
}
