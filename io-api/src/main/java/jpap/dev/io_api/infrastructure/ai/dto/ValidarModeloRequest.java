package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.lp.ModeloLP;

public record ValidarModeloRequest(
        String descripcionProblema,
        ModeloLP modelo
) {}
