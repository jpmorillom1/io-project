package jpap.dev.io_api.domain.lp;

import java.util.List;

public record FuncionObjetivo(
        List<Double> coeficientes,
        TipoObjetivo tipo
) {}
