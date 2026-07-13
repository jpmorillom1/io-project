package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * explicarSensibilidad es la única @Tool del proyecto SIN parámetros: no los necesita
 * porque el sesionId viaja en el ChatContextStore. Esta prueba fija esa forma —
 * si alguien le añadiera un parámetro, Groq tendría que rellenarlo y volveríamos al
 * terreno de los tool_use_failed que el resto de tools esquivan a mano.
 */
class SensibilidadToolSchemaTest {

    @Test
    void el_esquema_de_explicar_sensibilidad_se_genera_sin_parametros() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new SensibilidadTool(null, null, null));

        ToolSpecification spec = specs.stream()
                .filter(s -> s.name().equals("explicarSensibilidad"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("debe generarse la especificación de explicarSensibilidad"));

        assertTrue(spec.parameters() == null || spec.parameters().properties().isEmpty(),
                "la tool no debe exigir ningún argumento al LLM");
    }
}
