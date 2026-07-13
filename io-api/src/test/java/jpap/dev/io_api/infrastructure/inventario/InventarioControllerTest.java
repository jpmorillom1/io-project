package jpap.dev.io_api.infrastructure.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import jpap.dev.io_api.application.inventario.InventarioService;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;
import jpap.dev.io_api.domain.inventario.TramoDescuento;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica los endpoints REST sin levantar el contexto Spring: instancia el controller
 * con el service real y comprueba que se resuelve y se serializa a JSON.
 */
class InventarioControllerTest {

    private final InventarioController controller = new InventarioController(new InventarioService());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void endpoint_eoq_basico_resuelve_y_es_serializable() throws Exception {
        ModeloInventario modelo = new ModeloInventario(null, 1000.0, 50.0, 4.0,
                null, null, null, null, null, null);   // el endpoint fuerza el metodo

        ResponseEntity<SolveResult<SolucionInventario>> resp = controller.eoqBasico(modelo);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(158.113883, resp.getBody().solution().cantidadOptima(), 1e-4);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"cantidadOptima\""));
        assertTrue(json.contains("\"costoTotalAnual\""));
        assertTrue(json.contains("\"interpretacionPolitica\""));
        assertTrue(json.contains("\"tipo\":\"INVENTARIO\""));
    }

    @Test
    void endpoint_eoq_descuentos_elige_tramo_y_es_serializable() throws Exception {
        ModeloInventario modelo = new ModeloInventario(MetodoInventario.EOQ_BASICO, 5000.0, 49.0, null,
                null, null, null, null, 0.2,
                List.of(new TramoDescuento(0, 5.00),
                        new TramoDescuento(1000, 4.80),
                        new TramoDescuento(2500, 4.75)));

        ResponseEntity<SolveResult<SolucionInventario>> resp = controller.eoqDescuentos(modelo);

        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(4.80, resp.getBody().solution().precioUnitarioOptimo(), 1e-9);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"comparativa\""));
        assertTrue(json.contains("\"precioUnitarioOptimo\""));
    }

    @Test
    void entrada_malformada_lanza_illegal_argument() {
        ModeloInventario modelo = new ModeloInventario(null, null, 50.0, 4.0,
                null, null, null, null, null, null);   // sin demanda
        assertThrows(IllegalArgumentException.class, () -> controller.eoqBasico(modelo));
    }
}
