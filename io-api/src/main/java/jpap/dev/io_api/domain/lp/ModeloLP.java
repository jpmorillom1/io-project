package jpap.dev.io_api.domain.lp;

import jpap.dev.io_api.domain.common.ModeloResoluble;

import java.util.List;

public record ModeloLP(
        List<String> variables,
        FuncionObjetivo objetivo,
        List<Restriccion> restricciones
) implements ModeloResoluble {}
