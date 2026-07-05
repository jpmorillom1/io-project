package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;

/**
 * Solicitud de aprobación humana pendiente, incluida en ChatResponse cuando el
 * tutor quiso resolver. La UI muestra el modelo y el método con botones
 * Aprobar/Rechazar, y responde vía POST /api/v1/ai/chat/aprobacion con el solicitudId.
 *
 * El modelo es genérico ({@link ModeloResoluble}): puede ser un ModeloLP o un
 * ModeloTransporte según el método. La UI decide cómo pintarlo según {@code metodo}.
 */
public record SolicitudAprobacion(
        String solicitudId,
        MetodoResolucion metodo,
        ModeloResoluble modelo
) {}
