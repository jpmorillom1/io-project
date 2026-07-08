package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Igual que RedToolSchemaTest: AiServices.tools() genera el esquema JSON del @Tool via
 * ToolSpecifications, y un parametro con genericos anidados rompe el arranque. Aqui la
 * tabla de descuentos va como List&lt;TramoInput&gt; (record) y los opcionales llevan
 * required=false: nada de genericos anidados.
 */
class InventarioToolSchemaTest {

    @Test
    void los_esquemas_de_inventario_se_generan_sin_error() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new InventarioTool(null, null));

        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverInventario")),
                "debe generarse la especificacion de resolverInventario sin lanzar excepcion");
        assertTrue(specs.stream().anyMatch(s -> s.name().equals("resolverInventarioDescuentos")),
                "debe generarse la especificacion de resolverInventarioDescuentos sin lanzar excepcion");
    }
}
