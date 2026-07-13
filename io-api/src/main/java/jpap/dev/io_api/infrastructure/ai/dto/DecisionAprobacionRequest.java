package jpap.dev.io_api.infrastructure.ai.dto;

/**
 * Cuerpo de POST /api/v1/ai/chat/aprobacion — decisión del estudiante sobre
 * una solicitud de aprobación pendiente.
 *
 * - solicitudId: el id recibido en ChatResponse.solicitudAprobacion
 * - aprobado:    true ejecuta el solver; false lo cancela
 * - comentario:  opcional; en un rechazo explica qué está mal del modelo y
 *                re-alimenta al tutor para proponer la corrección
 */
import tools.jackson.databind.JsonNode;

public record DecisionAprobacionRequest(
        String solicitudId,
        boolean aprobado,
        String comentario,
        JsonNode modeloModificado
) {}
