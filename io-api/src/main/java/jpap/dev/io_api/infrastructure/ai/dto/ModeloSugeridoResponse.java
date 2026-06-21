package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.lp.ModeloLP;

import java.util.List;

public record ModeloSugeridoResponse(
        ModeloLP modelo,
        String razonamiento,
        List<String> supuestosAplicados,
        List<String> advertencias
) {}
