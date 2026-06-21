package jpap.dev.io_api.domain.lp;

import java.util.List;

public record ModeloLP(
        List<String> variables,
        FuncionObjetivo objetivo,
        List<Restriccion> restricciones
) {}
