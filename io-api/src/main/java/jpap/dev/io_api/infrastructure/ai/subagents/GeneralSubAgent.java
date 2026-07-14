package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente de conversación general: saludos, qué es la plataforma y teoría de
 * Investigación Operativa no ligada a un problema concreto. Sin tools de resolución.
 */
public interface GeneralSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
