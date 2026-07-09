package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente especializado en Gestión de Inventarios Deterministas (EOQ, Descuentos, POQ, Punto de Reorden).
 */
public interface InventarioSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
