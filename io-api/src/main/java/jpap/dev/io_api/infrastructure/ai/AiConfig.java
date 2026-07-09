package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jpap.dev.io_api.infrastructure.ai.subagents.*;
import jpap.dev.io_api.infrastructure.ai.supervisor.ModuloClassifierService;
import jpap.dev.io_api.infrastructure.ai.tools.*;
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

    @Bean
    public PlSubAgent plSubAgent(ChatModel chatModel,
                                 SimplexTool simplexTool,
                                 SugerirModeloTool sugerirTool,
                                 ValidarModeloTool validarTool,
                                 GranMTool granMTool,
                                 DosFasesTool dosFasesTool,
                                 GraficoTool graficoTool,
                                 ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/pl_system_prompt.txt");
        return AiServices.builder(PlSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool, graficoTool)
                .maxSequentialToolsInvocations(6)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public InventarioSubAgent inventarioSubAgent(ChatModel chatModel,
                                                 InventarioTool inventarioTool,
                                                 ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/inventario_system_prompt.txt");
        return AiServices.builder(InventarioSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(inventarioTool)
                .maxSequentialToolsInvocations(4)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public TransporteSubAgent transporteSubAgent(ChatModel chatModel,
                                                 TransporteTool transporteTool,
                                                 ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/transporte_system_prompt.txt");
        return AiServices.builder(TransporteSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(transporteTool)
                .maxSequentialToolsInvocations(4)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public RedesSubAgent redesSubAgent(ChatModel chatModel,
                                       RedTool redTool,
                                       ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/redes_system_prompt.txt");
        return AiServices.builder(RedesSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(redTool)
                .maxSequentialToolsInvocations(4)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public EnteraSubAgent enteraSubAgent(ChatModel chatModel,
                                         EnteraTool enteraTool,
                                         SugerirModeloTool sugerirTool,
                                         ValidarModeloTool validarTool,
                                         ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/entera_system_prompt.txt");
        return AiServices.builder(EnteraSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(enteraTool, sugerirTool, validarTool)
                .maxSequentialToolsInvocations(5)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public DinamicaSubAgent dinamicaSubAgent(ChatModel chatModel,
                                             DinamicaTool dinamicaTool,
                                             ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/dinamica_system_prompt.txt");
        return AiServices.builder(DinamicaSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(dinamicaTool)
                .maxSequentialToolsInvocations(4)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    /**
     * Tutor monolítico heredado (compatibilidad retroactiva).
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
                                         DinamicaTool dinamicaTool,
                                         ContentRetriever contentRetriever)
            throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
        return AiServices.builder(TutorAiService.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(14))
                .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool, graficoTool, transporteTool, redTool, enteraTool, inventarioTool, dinamicaTool)
                .maxSequentialToolsInvocations(6)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public ModuloClassifierService moduloClassifierService(ChatModel chatModel) {
        return AiServices.builder(ModuloClassifierService.class)
                .chatModel(new RetryingChatModel(chatModel))
                .build();
    }

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
