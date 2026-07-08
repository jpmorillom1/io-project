package jpap.dev.io_api.infrastructure.entera;

import com.fasterxml.jackson.databind.ObjectMapper;
import jpap.dev.io_api.application.entera.EnteraService;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.SolucionEntera;
import jpap.dev.io_api.domain.entera.TipoVariable;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el endpoint REST sin levantar el contexto Spring: instancia el controller con el
 * service real y comprueba que el resultado se resuelve y se serializa a JSON (steps del árbol,
 * valorRelajacion, valores enteros, etc.).
 */
class EnteraControllerTest {

    private final EnteraController controller = new EnteraController(new EnteraService());
    private final ObjectMapper mapper = new ObjectMapper();

    /** MAX 5x1+4x2, s.a. 6x1+4x2 ≤ 24, x1+2x2 ≤ 6, x1,x2 enteras → (4,0) Z=20. */
    private ModeloEntero modeloClasico() {
        return new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                        List.of(
                                new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24),
                                new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6))),
                List.of(TipoVariable.ENTERA, TipoVariable.ENTERA));
    }

    @Test
    void endpoint_branch_and_bound_resuelve_y_es_serializable() throws Exception {
        ResponseEntity<SolveResult<SolucionEntera>> resp = controller.resolver(modeloClasico());

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(20.0, resp.getBody().solution().valorOptimo(), 1e-6);
        assertEquals(21.0, resp.getBody().solution().valorRelajacion(), 1e-6);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"valorOptimo\""));
        assertTrue(json.contains("\"valorRelajacion\""));
        assertTrue(json.contains("\"nodosExplorados\""));
        assertTrue(json.contains("\"steps\""));
    }

    @Test
    void entrada_malformada_lanza_illegal_argument() {
        // tiposVariable con menos tipos que variables → IllegalArgumentException,
        // que GlobalExceptionHandler mapea a HTTP 400 en producción.
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                        List.of(new Restriccion(List.of(1.0, 1.0), TipoRestriccion.LEQ, 5))),
                List.of(TipoVariable.ENTERA));

        assertThrows(IllegalArgumentException.class, () -> controller.resolver(modelo));
    }
}
