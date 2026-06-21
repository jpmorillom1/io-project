package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jpap.dev.io_api.infrastructure.ai.tools.SimplexTool;
import jpap.dev.io_api.infrastructure.ai.tools.SugerirModeloTool;
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
     * - Tres tools registradas: el LLM actualiza la UI vía ChatContextStore
     */
    @Bean
    public TutorAiService tutorAiService(ChatModel chatModel,
                                         SimplexTool simplexTool,
                                         SugerirModeloTool sugerirTool,
                                         ValidarModeloTool validarTool)
            throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
        return AiServices.builder(TutorAiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(30))
                .tools(simplexTool, sugerirTool, validarTool)
                .systemMessageProvider(memId -> systemPrompt)
                .build();
    }

    /**
     * Servicio sin memoria para extracción y validación estructurada de modelos.
     * Usa los @SystemMessage definidos en los métodos de la interfaz.
     */
    @Bean
    public ModeloAiService modeloAiService(ChatModel chatModel) {
        return AiServices.builder(ModeloAiService.class)
                .chatModel(chatModel)
                .build();
    }

    private String cargarPrompt(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
