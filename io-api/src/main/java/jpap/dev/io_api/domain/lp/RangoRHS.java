package jpap.dev.io_api.domain.lp;

public record RangoRHS(
        String restriccion,   // "R1", "R2", ...
        double valorActual,
        Double min,           // null = -∞
        Double max            // null = +∞
) {}
