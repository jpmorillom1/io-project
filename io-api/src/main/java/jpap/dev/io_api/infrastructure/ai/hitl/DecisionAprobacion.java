package jpap.dev.io_api.infrastructure.ai.hitl;

/**
 * Decisión del humano sobre una solicitud de aprobación.
 * Es el valor con el que se completa el PendingResponse de la compuerta HITL:
 * el paso resolutor del workflow la lee del AgenticScope para decidir si ejecuta el solver.
 */
public record DecisionAprobacion(boolean aprobado, String comentario) {
}
