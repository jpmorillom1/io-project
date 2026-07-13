package jpap.dev.io_api.infrastructure.health;

import java.time.Instant;

public record HealthResponse(
        EstadoServicio estado,
        EstadoServicio baseDeDatos,
        EstadoServicio chromaDb,
        EstadoServicio llm,
        Instant verificadoEn
) {}
