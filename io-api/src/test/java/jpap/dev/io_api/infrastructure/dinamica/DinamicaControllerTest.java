package jpap.dev.io_api.infrastructure.dinamica;

import com.fasterxml.jackson.databind.ObjectMapper;
import jpap.dev.io_api.application.dinamica.DinamicaService;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.ArcoRuta;
import jpap.dev.io_api.domain.dinamica.ArticuloMochila;
import jpap.dev.io_api.domain.dinamica.EtapaRuta;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica los endpoints REST sin levantar el contexto Spring: instancia el controller
 * con el service real y comprueba que se resuelve y se serializa a JSON.
 *
 * El chequeo de serializacion importa de mas en este modulo: los estados inalcanzables valen
 * infinito internamente y Jackson los escribiria como el token Infinity, que no es JSON valido.
 */
class DinamicaControllerTest {

    private final DinamicaController controller = new DinamicaController(new DinamicaService());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void endpoint_mochila_resuelve_y_es_serializable() throws Exception {
        ModeloDinamico modelo = ModeloDinamico.builder()   // el endpoint fuerza el metodo
                .capacidad(5)
                .articulos(List.of(
                        new ArticuloMochila("A", 2, 3.0, null),
                        new ArticuloMochila("B", 3, 4.0, null),
                        new ArticuloMochila("C", 4, 5.0, null)))
                .build();

        ResponseEntity<SolveResult<SolucionDinamica>> resp = controller.mochila(modelo);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(7.0, resp.getBody().solution().valorOptimo(), 1e-9);

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"tipo\":\"PROGRAMACION_DINAMICA\""));
        assertTrue(json.contains("\"tablas\""));
        assertTrue(json.contains("\"politicaOptima\""));
        assertTrue(json.contains("\"funcionRecurrencia\""));
        assertTrue(json.contains("\"principioOptimalidad\""));
        assertTrue(json.contains("\"interpretacionPolitica\""));
        assertFalse(json.contains("Infinity"), "ningun infinito debe llegar al JSON");

        mapper.readTree(json);   // vuelve a parsearse: es JSON valido
    }

    @Test
    void endpoint_ruta_etapas_devuelve_la_ruta_optima() throws Exception {
        ModeloDinamico modelo = ModeloDinamico.builder()
                .etapasRuta(List.of(
                        new EtapaRuta(1, List.of("A")),
                        new EtapaRuta(2, List.of("B", "C")),
                        new EtapaRuta(3, List.of("D"))))
                .arcos(List.of(
                        new ArcoRuta("A", "B", 2), new ArcoRuta("A", "C", 4),
                        new ArcoRuta("B", "D", 7), new ArcoRuta("C", "D", 3)))
                .build();

        ResponseEntity<SolveResult<SolucionDinamica>> resp = controller.rutaEtapas(modelo);

        assertEquals(SolveStatus.OPTIMO, resp.getBody().status());
        assertEquals(7.0, resp.getBody().solution().valorOptimo(), 1e-9);   // A -> C -> D = 4 + 3
        assertEquals(List.of("A", "C", "D"), resp.getBody().solution().rutaOptima());

        String json = mapper.writeValueAsString(resp.getBody());
        assertTrue(json.contains("\"rutaOptima\""));
        mapper.readTree(json);
    }

    /** Un plan infactible es un resultado valido: HTTP 200, status INFACTIBLE y solution null. */
    @Test
    void endpoint_planificacion_produccion_infactible_devuelve_200_sin_solucion() {
        ModeloDinamico modelo = ModeloDinamico.builder()
                .demandas(List.of(3, 2))
                .costoPreparacion(3.0)
                .costoUnitarioProduccion(1.0)
                .costoMantener(1.0)
                .capacidadProduccion(1)
                .build();

        ResponseEntity<SolveResult<SolucionDinamica>> resp = controller.planificacionProduccion(modelo);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(SolveStatus.INFACTIBLE, resp.getBody().status());
        assertNull(resp.getBody().solution());
    }

    @Test
    void entrada_malformada_lanza_illegal_argument() {
        ModeloDinamico sinArticulos = ModeloDinamico.builder().capacidad(5).build();
        assertThrows(IllegalArgumentException.class, () -> controller.mochila(sinArticulos));
    }

    /**
     * El cuerpo JSON documentado en docs/API_CONTRACT.md debe bindear al record ModeloDinamico,
     * incluidos los records anidados y el enum de sentido. Es lo unico que @RequestBody hace y que
     * los demas tests, que construyen el modelo en Java, no ejercitan.
     */
    @Test
    void el_request_json_documentado_deserializa_a_modelo_dinamico() throws Exception {
        String json = """
                {
                  "sentido": "MINIMIZAR",
                  "etapasRuta": [
                    { "etapa": 1, "nodos": ["A"] },
                    { "etapa": 2, "nodos": ["B", "C"] },
                    { "etapa": 3, "nodos": ["D"] }
                  ],
                  "arcos": [
                    { "origen": "A", "destino": "B", "costo": 2 },
                    { "origen": "A", "destino": "C", "costo": 4 },
                    { "origen": "B", "destino": "D", "costo": 7 },
                    { "origen": "C", "destino": "D", "costo": 3 }
                  ]
                }
                """;

        ModeloDinamico modelo = mapper.readValue(json, ModeloDinamico.class);

        assertEquals(3, modelo.etapasRuta().size());
        assertEquals(List.of("B", "C"), modelo.etapasRuta().get(1).nodos());
        assertEquals("A", modelo.arcos().get(0).origen());
        assertEquals(2.0, modelo.arcos().get(0).costo(), 1e-9);
        assertNull(modelo.capacidad(), "los campos de otros submodelos quedan null");

        ResponseEntity<SolveResult<SolucionDinamica>> resp = controller.rutaEtapas(modelo);
        assertEquals(7.0, resp.getBody().solution().valorOptimo(), 1e-9);
    }

    /** La mochila 0/1 se pide omitiendo unidadesMaximas: debe llegar null, no romper el bind. */
    @Test
    void el_request_de_mochila_sin_unidades_maximas_deserializa() throws Exception {
        String json = """
                { "capacidad": 5,
                  "articulos": [ { "nombre": "A", "peso": 2, "valor": 3 } ] }
                """;

        ModeloDinamico modelo = mapper.readValue(json, ModeloDinamico.class);

        assertNull(modelo.articulos().get(0).unidadesMaximas());
        assertEquals(1, modelo.articulos().get(0).unidadesMaximasOrDefault());
        assertEquals(SolveStatus.OPTIMO, controller.mochila(modelo).getBody().status());
    }

    /** Un modelo sin metodo no llega nunca por REST, pero el service lo rechaza igual. */
    @Test
    void service_sin_metodo_lanza() {
        DinamicaService service = new DinamicaService();
        ModeloDinamico sinMetodo = ModeloDinamico.builder().capacidad(5).build();
        assertThrows(IllegalArgumentException.class, () -> service.resolver(sinMetodo));
        assertEquals(MetodoDinamico.MOCHILA, sinMetodo.conMetodo(MetodoDinamico.MOCHILA).metodo());
    }
}
