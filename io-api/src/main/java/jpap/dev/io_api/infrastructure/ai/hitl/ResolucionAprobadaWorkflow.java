package jpap.dev.io_api.infrastructure.ai.hitl;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import jpap.dev.io_api.domain.common.ModeloResoluble;

/**
 * Workflow agéntico de resolución con aprobación humana (Human-in-the-Loop).
 *
 * Secuencia: [compuerta HumanInTheLoop] → [acción resolutora].
 * La compuerta publica un PendingResponse en el AgenticScope y la acción
 * resolutora se bloquea al leerlo, hasta que el endpoint de aprobación lo
 * completa con la DecisionAprobacion del estudiante.
 *
 * El @MemoryId (solicitudId) registra el scope, lo que permite recuperarlo
 * desde el endpoint REST vía AgenticScopeAccess y evacuarlo al terminar.
 */
public interface ResolucionAprobadaWorkflow extends AgenticScopeAccess {

    @Agent("Resuelve un modelo solo después de la aprobación explícita del estudiante")
    String resolver(@MemoryId String solicitudId,
                    @V("modelo") ModeloResoluble modelo,
                    @V("metodo") MetodoResolucion metodo);
}
