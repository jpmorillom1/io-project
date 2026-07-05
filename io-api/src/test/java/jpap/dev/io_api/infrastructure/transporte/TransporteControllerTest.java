package jpap.dev.io_api.infrastructure.transporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import jpap.dev.io_api.application.transporte.TransporteService;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el endpoint REST sin levantar el contexto Spring: instancia el controller
 * con el service real y comprueba que el resultado se resuelve y se serializa a JSON
 * (incluyendo los int[] y List<int[]> de los pasos de MODI: celda, ciclo, etc.).
 */
class TransporteControllerTest {

    private final TransporteController controller = new TransporteController(new TransporteService());
    private final ObjectMapper mapper = new ObjectMapper();

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
                MetodoTransporte.ESQUINA_NOROESTE);   // el endpoint fuerza MODI
    }

    @Test
    void endpoint_modi_resuelve_y_es_serializable() throws Exception {
        ResponseEntity<SolveResult<SolucionTransporte>> resp = controller.modi(problema());

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(240.0, resp.getBody().solution().costoTotal(), 1e-6);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"costoTotal\""));
        assertTrue(json.contains("\"asignaciones\""));
        assertTrue(json.contains("\"comparativaInicial\""));
        assertTrue(json.contains("\"steps\""));
        assertTrue(json.contains("\"tipo\":\"TRANSPORTE\""));
    }

    @Test
    void endpoint_esquina_noroeste_usa_su_propio_metodo() {
        ResponseEntity<SolveResult<SolucionTransporte>> resp = controller.esquinaNoroeste(problema());
        assertEquals(380.0, resp.getBody().solution().costoTotal(), 1e-6);
        // La esquina noroeste no calcula comparativa (eso es exclusivo de MODI).
        assertNull(resp.getBody().solution().comparativaInicial());
    }
}
