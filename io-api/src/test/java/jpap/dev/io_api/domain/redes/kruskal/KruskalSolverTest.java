package jpap.dev.io_api.domain.redes.kruskal;

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
 * Grafo no dirigido con MST conocido (verificado a mano):
 *   A—B(1), B—C(2), A—C(3), C—D(4)
 * Kruskal acepta A—B, B—C y C—D (peso total 7) y rechaza A—C por formar ciclo.
 */
class KruskalSolverTest {

    private static final double DELTA = 1e-6;

    private final KruskalSolver solver = new KruskalSolver();

    private Arista arista(String origen, String destino, double peso) {
        return new Arista(origen, destino, peso, null, null);
    }

    private ModeloRed problema() {
        return new ModeloRed(
                List.of("A", "B", "C", "D"),
                List.of(
                        arista("A", "B", 1),
                        arista("B", "C", 2),
                        arista("A", "C", 3),
                        arista("C", "D", 4)),
                false, MetodoRed.KRUSKAL, null, null, null, null, null);
    }

    @Test
    void encuentraElArbolDePesoMinimoConocido() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals(SolveStatus.OPTIMO, r.status());
        assertEquals(7.0, r.solution().valorObjetivo(), DELTA);
        assertEquals(3, r.solution().aristasSolucion().size());
        // A—C (peso 3) queda fuera del árbol
        assertFalse(r.solution().aristasSolucion().contains(arista("A", "C", 3)));
    }

    @Test
    void rechazaLaAristaQueFormaCiclo() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertTrue(r.steps().stream().anyMatch(s ->
                        s.titulo().startsWith("Rechazar A—C") && s.descripcion().contains("ciclo")),
                "debe existir un paso que rechace A—C por formar ciclo");
    }

    @Test
    void grafoDesconexoEsInfactible() {
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B", "C", "D"),
                List.of(arista("A", "B", 1), arista("C", "D", 2)),
                false, MetodoRed.KRUSKAL, null, null, null, null, null);

        SolveResult<SolucionRed> r = solver.resolver(modelo);

        assertEquals(SolveStatus.INFACTIBLE, r.status());
        assertNull(r.solution());
    }

    @Test
    void losPasosLlevanElGrafoParaLaUi() {
        SolveResult<SolucionRed> r = solver.resolver(problema());

        assertEquals("REDES", r.steps().get(0).datos().get("tipo"));
        assertEquals("KRUSKAL", r.steps().get(0).datos().get("metodo"));
    }
}
