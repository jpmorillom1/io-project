package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente especializado en Programación Lineal Continua (Simplex, Gran M, Dos Fases, Gráfico).
 */
public interface PlSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
