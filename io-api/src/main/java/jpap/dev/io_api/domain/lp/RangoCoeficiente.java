package jpap.dev.io_api.domain.lp;

public record RangoCoeficiente(
        String variable,
        double valorActual,
        Double min,   // null = -∞
        Double max    // null = +∞
) {}
