package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente especializado en Programación Lineal Entera y Binaria (Branch & Bound).
 */
public interface EnteraSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
