package jpap.dev.io_api.infrastructure.ai.subagents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Subagente especializado en Redes y Grafos (Dijkstra, Kruskal, Edmonds-Karp, Flujo de Costo Mínimo, Húngaro).
 */
public interface RedesSubAgent {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
