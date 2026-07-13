package jpap.dev.io_api.domain.redes.dijkstra;

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
 * Grafo dirigido con resultado conocido (verificado a mano):
 *   A→B(4), A→C(2), C→B(1), B→D(5), C→D(8), C→E(10), D→E(2)
 * Ruta más corta A→E: A → C → B → D → E con distancia 10.
 */
class DijkstraSolverTest {

    private static final double DELTA = 1e-6;

    private final DijkstraSolver solver = new DijkstraSolver();

    private Arista arista(String origen, String destino, double peso) {
        return new Arista(origen, destino, peso, null, null);
    }

    private ModeloRed problema(String sumidero) {
        return new ModeloRed(
                List.of("A", "B", "C", "D", "E"),
                List.of(
                        arista("A", "B", 4),
                        arista("A", "C", 2),
                        arista("C", "B", 1),
                        arista("B", "D", 5),
                        arista("C", "D", 8),
                        arista("C", "E", 10),
                        arista("D", "E", 2)),
                true, MetodoRed.DIJKSTRA, "A", sumidero, null, null, null);
    }

    @Test
    void encuentraLaRutaMasCortaConocida() {
        SolveResult<SolucionRed> r = solver.resolver(problema("E"));

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(10.0, r.solution().valorObjetivo(), DELTA);
        assertEquals(List.of("A", "C", "B", "D", "E"), r.solution().rutaOptima());
        assertEquals(4, r.solution().aristasSolucion().size());

        assertEquals(0.0, r.solution().distancias().get("A"), DELTA);
        assertEquals(3.0, r.solution().distancias().get("B"), DELTA);
        assertEquals(2.0, r.solution().distancias().get("C"), DELTA);
        assertEquals(8.0, r.solution().distancias().get("D"), DELTA);
        assertEquals(10.0, r.solution().distancias().get("E"), DELTA);
    }

    @Test
    void sinSumideroDevuelveLasDistanciasATodos() {
        SolveResult<SolucionRed> r = solver.resolver(problema(null));

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertNull(r.solution().rutaOptima());
        assertEquals(5, r.solution().distancias().size());
        assertEquals(10.0, r.solution().distancias().get("E"), DELTA);
    }

    @Test
    void pesoNegativoLanzaExcepcion() {
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B"),
                List.of(arista("A", "B", -3)),
                true, MetodoRed.DIJKSTRA, "A", "B", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo));
    }

    @Test
    void sumideroInalcanzableEsInfactible() {
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B", "C"),
                List.of(arista("A", "B", 1)),
                true, MetodoRed.DIJKSTRA, "A", "C", null, null, null);

        SolveResult<SolucionRed> r = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, r.status());
        assertNull(r.solution());
    }

    @Test
    void losPasosLlevanElGrafoParaLaUi() {
        SolveResult<SolucionRed> r = solver.resolver(problema("E"));

        assertFalse(r.steps().isEmpty());
        assertEquals(0, r.steps().get(0).numero());
        assertEquals("REDES", r.steps().get(0).datos().get("tipo"));
        assertEquals("DIJKSTRA", r.steps().get(0).datos().get("metodo"));
        assertNotNull(r.steps().get(0).datos().get("aristas"));
    }
}
