package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Igual que TransporteToolSchemaTest: AiServices.tools() genera el esquema JSON de cada
 * @Tool vía ToolSpecifications, y un parámetro con genéricos anidados rompe el arranque.
 * Las aristas van como List&lt;AristaInput&gt; y la matriz como List&lt;FilaCostos&gt; (records).
 */
class RedToolSchemaTest {

    @Test
    void los_esquemas_de_las_tools_de_redes_se_generan_sin_error() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new RedTool(null, null));

        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverRed")),
                "debe generarse la especificación de resolverRed sin lanzar excepción");
        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverAsignacion")),
                "debe generarse la especificación de resolverAsignacion sin lanzar excepción");
    }
}
