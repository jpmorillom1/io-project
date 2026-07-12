package jpap.dev.io_api.infrastructure.ai.dto;

import java.time.LocalDateTime;

/**
 * Una conversación tal como la lista la barra lateral del chat.
 *
 * `moduloActivo` es el nombre de un TutorSupervisorService.ModuloIO (PL, TRANSPORTE, …) y
 * puede ser null si la sesión aún no se ha clasificado.
 */
public record ResumenSesion(
        String sesionId,
        String titulo,
        String moduloActivo,
        LocalDateTime actualizada
) {}
