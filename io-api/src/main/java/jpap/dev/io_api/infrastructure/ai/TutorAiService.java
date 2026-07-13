package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Tutor socrático conversacional con memoria de sesión.
 * El system prompt se inyecta desde el archivo prompts/tutor_system_prompt.txt
 * en AiConfig para no hardcodearlo aquí.
 * Los @Tool registrados en AiConfig (SimplexTool) son accesibles automáticamente.
 */
public interface TutorAiService {

    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}
