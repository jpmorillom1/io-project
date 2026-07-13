package jpap.dev.io_api.infrastructure.ai.dto;

import java.time.LocalDateTime;

/**
 * Un mensaje del transcript tal como lo pinta la UI.
 * `rol` es "user" o "tutor" — los mismos valores que usa io-ui.
 */
public record MensajeHistorial(
        String rol,
        String texto,
        LocalDateTime fecha
) {}
