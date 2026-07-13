package jpap.dev.io_api.domain.transporte.modi;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.transporte.CostoPorMetodo;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModiSolverTest {

    private static final double DELTA = 1e-5;
    private final ModiSolver solver = new ModiSolver();

    /** Problema 3×3 cuyo óptimo es 240 (demostrado por dualidad: todos los costos reducidos ≥ 0). */
    private ModeloTransporte problema() {
        return new ModeloTransporte(
                List.of("O1", "O2", "O3"),
                List.of("D1", "D2", "D3"),
                List.of(20.0, 30.0, 25.0),
                List.of(30.0, 25.0, 20.0),
                List.of(
                        List.of(4.0, 6.0, 8.0),
                        List.of(6.0, 4.0, 2.0),
                        List.of(2.0, 8.0, 6.0)),
                MetodoTransporte.MODI);
    }

    @Test
    void encuentra_el_optimo_240() {
        SolveResult<SolucionTransporte> r = solver.resolver(problema());
        assertTrue(r.status() == SolveStatus.OPTIMO || r.status() == SolveStatus.MULTIPLE_OPTIMO);
        assertEquals(240.0, r.solution().costoTotal(), DELTA);
    }

    @Test
    void reporta_la_comparativa_de_los_tres_metodos_iniciales() {
        SolucionTransporte sol = solver.resolver(problema()).solution();
        assertNotNull(sol.comparativaInicial());
        assertEquals(3, sol.comparativaInicial().size());

        double nw = costoDe(sol.comparativaInicial(), MetodoTransporte.ESQUINA_NOROESTE);
        double cm = costoDe(sol.comparativaInicial(), MetodoTransporte.COSTO_MINIMO);
        double vg = costoDe(sol.comparativaInicial(), MetodoTransporte.VOGEL);
        assertEquals(380.0, nw, DELTA);
        assertEquals(240.0, cm, DELTA);
        assertEquals(240.0, vg, DELTA);

        // Arranca desde el más barato (empate CM/Vogel → CM por desempate).
        assertEquals(MetodoTransporte.COSTO_MINIMO, sol.metodoInicial());
    }

    @Test
    void mejora_o_iguala_a_todos_los_metodos_iniciales() {
        SolucionTransporte sol = solver.resolver(problema()).solution();
        for (CostoPorMetodo c : sol.comparativaInicial()) {
            assertTrue(sol.costoTotal() <= c.costoInicial() + DELTA,
                    "MODI (" + sol.costoTotal() + ") no debe superar a " + c.metodo() + " (" + c.costoInicial() + ")");
        }
    }

    @Test
    void optimo_provable_en_problema_diagonal_es_30() {
        // Costos con un 1 barato fuera de la diagonal noroeste: cota inferior 30 (30 unidades × ≥1),
        // alcanzable con x13=x22=x31=10 → óptimo demostrable = 30. La esquina noroeste daría 170.
        ModeloTransporte diagonal = new ModeloTransporte(
                List.of("O1", "O2", "O3"),
                List.of("D1", "D2", "D3"),
                List.of(10.0, 10.0, 10.0),
                List.of(10.0, 10.0, 10.0),
                List.of(
                        List.of(8.0, 8.0, 1.0),
                        List.of(8.0, 1.0, 8.0),
                        List.of(1.0, 8.0, 8.0)),
                MetodoTransporte.MODI);

        SolucionTransporte sol = solver.resolver(diagonal).solution();
        assertEquals(30.0, sol.costoTotal(), DELTA);
        assertEquals(170.0, costoDe(sol.comparativaInicial(), MetodoTransporte.ESQUINA_NOROESTE), DELTA);
    }

    @Test
    void itera_reduciendo_el_costo_desde_la_mejor_inicial() {
        // Ejemplo clásico (Taha): Vogel arranca en 102 y MODI reasigna (θ > 0) hasta el óptimo 100.
        ModeloTransporte clasico = new ModeloTransporte(
                List.of("S1", "S2", "S3"),
                List.of("D1", "D2", "D3", "D4"),
                List.of(6.0, 1.0, 10.0),
                List.of(7.0, 5.0, 3.0, 2.0),
                List.of(
                        List.of(2.0, 3.0, 11.0, 7.0),
                        List.of(1.0, 0.0, 6.0, 1.0),
                        List.of(5.0, 8.0, 15.0, 9.0)),
                MetodoTransporte.MODI);

        SolveResult<SolucionTransporte> r = solver.resolver(clasico);
        SolucionTransporte sol = r.solution();

        assertEquals(100.0, sol.costoTotal(), DELTA);
        assertEquals(MetodoTransporte.VOGEL, sol.metodoInicial());
        double vogel = costoDe(sol.comparativaInicial(), MetodoTransporte.VOGEL);
        assertTrue(sol.costoTotal() < vogel - DELTA,
                "MODI debe mejorar estrictamente sobre Vogel (" + vogel + " → " + sol.costoTotal() + ")");

        long iteraciones = r.steps().stream().filter(s -> s.titulo().startsWith("Iteración")).count();
        assertTrue(iteraciones >= 1, "debe registrar al menos una iteración de mejora");

        double[] oferta = {6, 1, 10};
        double[] demanda = {7, 5, 3, 2};
        for (int i = 0; i < 3; i++) {
            double suma = 0;
            for (int j = 0; j < 4; j++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(oferta[i], suma, DELTA);
        }
        for (int j = 0; j < 4; j++) {
            double suma = 0;
            for (int i = 0; i < 3; i++) suma += sol.asignaciones().get(i).get(j);
            assertEquals(demanda[j], suma, DELTA);
        }
    }

    @Test
    void balancea_agregando_destino_ficticio_cuando_sobra_oferta() {
        // Σoferta=30 > Σdemanda=25 → destino ficticio con demanda 5.
        ModeloTransporte desbalanceado = new ModeloTransporte(
                List.of("O1", "O2"),
                List.of("D1", "D2"),
                List.of(20.0, 10.0),
                List.of(15.0, 10.0),
                List.of(
                        List.of(2.0, 4.0),
                        List.of(3.0, 1.0)),
                MetodoTransporte.MODI);

        SolucionTransporte sol = solver.resolver(desbalanceado).solution();
        assertEquals(3, sol.destinos().size());
        assertTrue(sol.destinos().contains("Ficticio"));
        assertEquals(2, sol.origenes().size());
    }

    @Test
    void dimensiones_incoherentes_lanzan_excepcion() {
        ModeloTransporte malo = new ModeloTransporte(
                List.of("O1", "O2"),
                List.of("D1", "D2"),
                List.of(10.0, 10.0),
                List.of(10.0, 10.0),
                List.of(List.of(2.0, 4.0)),   // solo 1 fila de costos para 2 orígenes
                MetodoTransporte.MODI);
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(malo));
    }

    private static double costoDe(List<CostoPorMetodo> comp, MetodoTransporte metodo) {
        return comp.stream().filter(c -> c.metodo() == metodo).findFirst().orElseThrow().costoInicial();
    }
}
