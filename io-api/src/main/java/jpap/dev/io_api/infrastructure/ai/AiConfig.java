package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jpap.dev.io_api.infrastructure.ai.subagents.*;
import jpap.dev.io_api.infrastructure.ai.supervisor.ModuloClassifierService;
import jpap.dev.io_api.infrastructure.ai.tools.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AiConfig {

    /** Tamaño de la ventana que el LLM ve en cada turno. La persistencia no la amplía. */
    private static final int MAX_MENSAJES_MEMORIA = 14;

    /** Un título son 6 palabras: el tope corta en seco cualquier verborrea del modelo. */
    private static final int MAX_TOKENS_TITULO = 24;

    private final ResourceLoader resourceLoader;
    private final ChatMemoryStore chatMemoryStore;

    public AiConfig(ResourceLoader resourceLoader, ChatMemoryStore chatMemoryStore) {
        this.resourceLoader = resourceLoader;
        this.chatMemoryStore = chatMemoryStore;
    }

    /**
     * Memoria respaldada en PostgreSQL, con el sesionId como memoryId: los seis subagentes
     * comparten una única ventana por sesión, así que cambiar de módulo no rompe el hilo.
     *
     * alwaysKeepSystemMessageFirst es obligatorio aquí: al enrutar a otro subagente entra un
     * system prompt distinto y MessageWindowChatMemory, por defecto, lo añadiría al FINAL de
     * la ventana en vez de a la cabecera.
     */
    private ChatMemoryProvider memoriaDeSesion() {
        return sesionId -> MessageWindowChatMemory.builder()
                .id(sesionId)
                .maxMessages(MAX_MENSAJES_MEMORIA)
                .chatMemoryStore(chatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }

    @Bean
    public PlSubAgent plSubAgent(ChatModel chatModel,
                                 SimplexTool simplexTool,
                                 SugerirModeloTool sugerirTool,
                                 ValidarModeloTool validarTool,
                                 GranMTool granMTool,
                                 DosFasesTool dosFasesTool,
                                 GraficoTool graficoTool,
                                 SensibilidadTool sensibilidadTool,
                                 ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/pl_system_prompt.txt");
        return AiServices.builder(PlSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memoriaDeSesion())
                .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool, graficoTool, sensibilidadTool)
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
                .chatMemoryProvider(memoriaDeSesion())
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
                .chatMemoryProvider(memoriaDeSesion())
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
                .chatMemoryProvider(memoriaDeSesion())
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
                .chatMemoryProvider(memoriaDeSesion())
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
                .chatMemoryProvider(memoriaDeSesion())
                .tools(dinamicaTool)
                .maxSequentialToolsInvocations(4)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "ERROR: los argumentos de la herramienta no cumplen su esquema: "
                                + error.getMessage() + " — corrige la llamada usando SOLO los campos del esquema."))
                .systemMessageProvider(memId -> systemPrompt)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public GeneralSubAgent generalSubAgent(ChatModel chatModel,
                                           ContentRetriever contentRetriever) throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/subagents/general_system_prompt.txt");
        return AiServices.builder(GeneralSubAgent.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memoriaDeSesion())
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
                                         SensibilidadTool sensibilidadTool,
                                         ContentRetriever contentRetriever)
            throws IOException {
        String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
        return AiServices.builder(TutorAiService.class)
                .chatModel(new RetryingChatModel(chatModel))
                .chatMemoryProvider(memoriaDeSesion())
                .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool, graficoTool, transporteTool, redTool, enteraTool, inventarioTool, dinamicaTool, sensibilidadTool)
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

    /**
     * Titular una conversación es una tarea de una línea: no justifica el modelo del tutor.
     * Va contra un modelo pequeño propio, sin memoria, sin tools y sin RAG — y sin
     * RetryingChatModel, porque aquí no hay tool calls que puedan salir malformadas y el
     * fallo ya está cubierto por el título provisional.
     */
    @Bean
    public TituladorAiService tituladorAiService(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${app.titulos.model-name}") String modelName) {
        ChatModel modeloPequeno = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .maxTokens(MAX_TOKENS_TITULO)
                .build();
        return AiServices.builder(TituladorAiService.class)
                .chatModel(modeloPequeno)
                .build();
    }

    /**
     * System prompt del agente + la regla de registro común a todos los módulos.
     *
     * Va en un único fichero (prompts/common/registro_explicacion.txt) y no copiada en los
     * siete prompts: es la misma regla para todos, y siete copias se habrían ido separando
     * al primer retoque. Se concatena al FINAL a propósito — es lo último que el modelo lee
     * antes del turno, y en la práctica eso pesa.
     */
    private String cargarPrompt(String location) throws IOException {
        return leer(location) + "\n" + leer("classpath:prompts/common/registro_explicacion.txt");
    }

    private String leer(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
