package jpap.dev.io_api.domain.common;

import java.util.Map;

public record SolveStep(
        int numero,
        String titulo,
        String descripcion,
        Map<String, Object> datos
) {}
