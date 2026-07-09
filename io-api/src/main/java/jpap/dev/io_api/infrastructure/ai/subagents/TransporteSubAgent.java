package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente especializado en Transporte y Asignación (Esquina Noroeste, Costo Mínimo, Vogel, MODI).
 */
public interface TransporteSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
