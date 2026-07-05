package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Decorador de ChatModel que reintenta cuando Groq falla al parsear la tool call
 * generada por el LLM (error 400 "tool_use_failed" con "failed_generation").
 *
 * llama-3.3 a veces emite la llamada a la función con sintaxis malformada
 * (p. ej. {@code <function=resolverSimplex,{...}</function>}); Groq responde 400 y
 * LangChain4j lo mapea a InvalidRequestException, que es NonRetriableException —
 * por eso el retry interno del modelo NO cubre este caso y lo hacemos aquí.
 *
 * Reintentar a nivel de ChatModel (y no re-llamando al AiService) es clave:
 * la memoria de sesión y las tools ya ejecutadas del turno no se ven afectadas.
 * Con temperature=0 la regeneración sería casi idéntica, así que los reintentos
 * van con temperatura más alta para romper el determinismo.
 */
@Slf4j
public class RetryingChatModel implements ChatModel {

    private static final int MAX_INTENTOS = 3;
    private static final double TEMPERATURA_REINTENTO = 0.4;

    private final ChatModel delegate;

    public RetryingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        RuntimeException ultimoError = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return delegate.chat(intento == 1 ? request : conTemperaturaDeReintento(request));
            } catch (RuntimeException e) {
                if (!esToolCallMalformada(e)) {
                    throw e;
                }
                ultimoError = e;
                log.warn("[AI/retry] intento {}/{} falló por tool call malformada del LLM: {}",
                        intento, MAX_INTENTOS, e.getMessage());
            }
        }
        log.error("[AI/retry] agotados {} intentos por tool call malformada", MAX_INTENTOS);
        throw ultimoError;
    }

    /** El 400 de Groq cuando no puede parsear la función que generó el propio LLM. */
    private boolean esToolCallMalformada(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("tool_use_failed")
                    || msg.contains("failed_generation")
                    || msg.contains("Failed to call a function"))) {
                return true;
            }
        }
        return false;
    }

    private ChatRequest conTemperaturaDeReintento(ChatRequest request) {
        ChatRequestParameters params = request.parameters().overrideWith(
                ChatRequestParameters.builder().temperature(TEMPERATURA_REINTENTO).build());
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(params)
                .build();
    }

    // ─── delegación directa del resto del contrato ─────────────────────────────

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
