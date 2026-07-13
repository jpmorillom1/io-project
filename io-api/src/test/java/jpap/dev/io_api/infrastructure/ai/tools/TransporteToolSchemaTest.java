package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduce el camino que fallaba en el arranque: AiServices.tools() genera el esquema JSON
 * de cada @Tool vía ToolSpecifications. Un parámetro con genéricos anidados (List&lt;List&lt;Double&gt;&gt;)
 * lanzaba ClassCastException; envolver la fila en el record FilaCostos lo resuelve.
 */
class TransporteToolSchemaTest {

    @Test
    void el_esquema_de_la_tool_de_transporte_se_genera_sin_error() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new TransporteTool(null, null));

        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverTransporte")),
                "debe generarse la especificación de resolverTransporte sin lanzar excepción");
    }
}
