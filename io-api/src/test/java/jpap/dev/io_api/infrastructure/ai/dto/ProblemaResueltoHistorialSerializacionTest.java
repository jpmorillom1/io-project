package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El historial viaja al frontend como JSON crudo dentro de JsonNode.
 *
 * Con el JsonNode de Jackson 2 el mapper de Spring Boot 4 (Jackson 3) no reconoce el árbol
 * y lo vuelca como un POJO: {"array":false,"nodeType":"OBJECT",...}. El workspace recibía
 * entonces un modelo sin `origenes` y el grafo de transporte reventaba.
 */
class ProblemaResueltoHistorialSerializacionTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("El modelo del último problema se serializa como JSON, no como los getters del nodo")
    void modeloViajaComoJsonCrudo() {
        ModeloTransporte modelo = new ModeloTransporte(
                List.of("Planta 1", "Planta 2"),
                List.of("Ciudad 1", "Ciudad 2"),
                List.of(10.0, 20.0),
                List.of(15.0, 15.0),
                List.of(List.of(4.0, 6.0), List.of(2.0, 8.0)),
                MetodoTransporte.MODI);

        JsonNode modeloNode = mapper.readTree(mapper.writeValueAsString(modelo));
        var historial = new ProblemaResueltoHistorial("TRANSPORTE", modeloNode, null);

        String json = mapper.writeValueAsString(historial);

        assertThat(json).contains("\"origenes\"").contains("Planta 1").contains("\"MODI\"");
        assertThat(json).doesNotContain("nodeType").doesNotContain("bigDecimal");
    }
}
