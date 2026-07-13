package jpap.dev.io_api.domain.lp;

import java.util.List;

public record RangosSensibilidad(
        List<RangoCoeficiente> coeficientesObjetivo,
        List<RangoRHS> rhs
) {}
