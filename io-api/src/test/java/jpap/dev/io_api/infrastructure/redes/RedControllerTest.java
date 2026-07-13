package jpap.dev.io_api.infrastructure.redes;

import com.fasterxml.jackson.databind.ObjectMapper;
import jpap.dev.io_api.application.redes.RedService;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica los endpoints REST sin levantar el contexto Spring: instancia el controller
 * con el service real y comprueba que el resultado se resuelve y se serializa a JSON
 * (steps con el grafo, distancias, asignación, etc.).
 */
class RedControllerTest {

    private final RedController controller = new RedController(new RedService());
    private final ObjectMapper mapper = new ObjectMapper();

    private Arista arista(String origen, String destino, double peso) {
        return new Arista(origen, destino, peso, null, null);
    }

    /** Grafo del DijkstraSolverTest: ruta A→E con distancia 10. */
    private ModeloRed grafo() {
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
                true, null, "A", "E", null, null, null);   // el endpoint fuerza el método
    }

    @Test
    void endpoint_dijkstra_resuelve_y_es_serializable() throws Exception {
        ResponseEntity<SolveResult<SolucionRed>> resp = controller.dijkstra(grafo());

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(10.0, resp.getBody().solution().valorObjetivo(), 1e-6);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"valorObjetivo\""));
        assertTrue(json.contains("\"distancias\""));
        assertTrue(json.contains("\"rutaOptima\""));
        assertTrue(json.contains("\"steps\""));
        assertTrue(json.contains("\"tipo\":\"REDES\""));
    }

    @Test
    void endpoint_kruskal_fuerza_su_propio_metodo() {
        // El grafo trae fuente/sumidero (que Kruskal ignora) y ningún método:
        // el endpoint debe forzar KRUSKAL y tratarlo como no dirigido.
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B", "C", "D"),
                List.of(
                        arista("A", "B", 1),
                        arista("B", "C", 2),
                        arista("A", "C", 3),
                        arista("C", "D", 4)),
                true, MetodoRed.DIJKSTRA, null, null, null, null, null);

        ResponseEntity<SolveResult<SolucionRed>> resp = controller.kruskal(modelo);

        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(7.0, resp.getBody().solution().valorObjetivo(), 1e-6);
    }

    @Test
    void endpoint_asignacion_resuelve_y_es_serializable() throws Exception {
        ModeloRed modelo = new ModeloRed(null, null, true, null, null, null,
                List.of("A1", "A2", "A3"),
                List.of("T1", "T2", "T3"),
                List.of(
                        List.of(9.0, 2.0, 7.0),
                        List.of(6.0, 4.0, 3.0),
                        List.of(5.0, 8.0, 1.0)));

        ResponseEntity<SolveResult<SolucionRed>> resp = controller.asignacion(modelo);

        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(9.0, resp.getBody().solution().costoTotal(), 1e-6);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"asignacion\""));
        assertTrue(json.contains("\"costoTotal\""));
    }

    @Test
    void entrada_malformada_lanza_illegal_argument() {
        // Sin fuente: entrada malformada para Dijkstra → IllegalArgumentException,
        // que GlobalExceptionHandler mapea a HTTP 400 en producción.
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B"),
                List.of(arista("A", "B", 1)),
                true, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> controller.dijkstra(modelo));
    }
}
