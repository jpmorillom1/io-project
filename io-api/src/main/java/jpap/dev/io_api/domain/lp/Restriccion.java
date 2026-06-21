package jpap.dev.io_api.domain.lp;

import java.util.List;

public record Restriccion(
        List<Double> coeficientes,
        TipoRestriccion tipo,
        double rhs
) {}
