package jpap.dev.io_api.infrastructure.ai.dto;

import java.util.List;

/**
 * Todo lo necesario para reabrir una conversación: el transcript que rehidrata el chat y el
 * último problema resuelto que rehidrata el workspace.
 *
 * `ultimoProblema` es null si la sesión nunca llegó a aprobar una resolución.
 */
public record HistorialSesion(
        String sesionId,
        String titulo,
        String moduloActivo,
        List<MensajeHistorial> mensajes,
        ProblemaResueltoHistorial ultimoProblema
) {}
