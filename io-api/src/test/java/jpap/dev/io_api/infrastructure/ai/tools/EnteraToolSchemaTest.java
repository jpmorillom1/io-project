package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Igual que RedToolSchemaTest: AiServices.tools() genera el esquema JSON del @Tool vía
 * ToolSpecifications, y un parámetro con genéricos anidados rompe el arranque. Aquí las
 * restricciones van como List&lt;RestriccionInput&gt; (record) y la integralidad como dos
 * List&lt;String&gt; — nada de genéricos anidados.
 */
class EnteraToolSchemaTest {

    @Test
    void el_esquema_de_resolver_entera_se_genera_sin_error() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new EnteraTool(null, null));

        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverEntera")),
                "debe generarse la especificación de resolverEntera sin lanzar excepción");
    }
}
