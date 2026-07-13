package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.lp.ModeloLP;

import java.util.List;

public record ValidacionResponse(
        boolean esValido,
        String analisis,
        List<String> erroresEncontrados,
        List<String> sugerencias,
        ModeloLP modeloCorregido    // null cuando esValido = true
) {}
