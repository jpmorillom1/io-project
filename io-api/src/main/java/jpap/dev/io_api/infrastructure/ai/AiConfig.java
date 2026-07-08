package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jpap.dev.io_api.infrastructure.ai.tools.DosFasesTool;
import jpap.dev.io_api.infrastructure.ai.tools.EnteraTool;
import jpap.dev.io_api.infrastructure.ai.tools.GraficoTool;
import jpap.dev.io_api.infrastructure.ai.tools.GranMTool;
import jpap.dev.io_api.infrastructure.ai.tools.InventarioTool;
import jpap.dev.io_api.infrastructure.ai.tools.RedTool;
import jpap.dev.io_api.infrastructure.ai.tools.SimplexTool;
import jpap.dev.io_api.infrastructure.ai.tools.SugerirModeloTool;
import jpap.dev.io_api.infrastructure.ai.tools.TransporteTool;
import jpap.dev.io_api.infrastructure.ai.tools.ValidarModeloTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AiConfig {

    private final ResourceLoader resourceLoader;

    public AiConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Tutor socrático conversacional.
     * - Memoria por sesión (hasta 30 mensajes)
     * - System prompt cargado desde fichero (fácil de editar sin recompilar)
     * - Tools registradas: el LLM actualiza la UI vía ChatContextStore
     */
    @Bean
    public TutorAiService tutorAiService(ChatModel chatModel,
                                         SimplexTool simplexTool,
                                         SugerirModeloTool sugerirTool,
                                         ValidarModeloTool validarTool,
                                         GranMTool granMTool,
                                         DosFasesTool dosFasesTool,
                                         GraficoTool graficoTool,
                                         TransporteTool transporteTool,
                                         RedTool redTool,
                                         EnteraTool enteraTool,
                                         InventarioTool inventarioTool,
                                         ContentRetriever contentRetriever)
            throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
        return AiServices.builder(TutorAiService.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(30))
                .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool, graficoTool, transporteTool, redTool, enteraTool, inventarioTool)
                // Red de seguridad ante bucles de tool calls del LLM (llama repite la misma
                // llamada a temperatura 0): al exceder el tope LangChain4j lanza y el
                // controlador degrada con el mensaje amable. El caso normal usa 1-3 tools.
                .maxSequentialToolsInvocations(6)
                // Argumentos malformados del LLM (JSON que no casa con el record del @Tool):
                // en vez de lanzar y tumbar el turno, el error vuelve al LLM como resultado
                // de la tool para que corrija la llamada en el siguiente paso.
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                        + error.getMessage()
                        + " — corrige la llamada usando SOLO los campos definidos en el esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    /**
     * Servicio sin memoria para extracción y validación estructurada de modelos.
     * Usa los @SystemMessage definidos en los métodos de la interfaz.
     */
    @Bean
    public ModeloAiService modeloAiService(ChatModel chatModel) {
        return AiServices.builder(ModeloAiService.class)
                .chatModel(new RetryingChatModel(chatModel))
                .build();
    }

    private String cargarPrompt(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
