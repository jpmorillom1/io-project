package jpap.dev.io_api.domain.lp;

import java.util.Map;

public record SolucionLP(
        Map<String, Double> valores,
        Map<String, Double> holguras,
        double valorOptimo,
        Map<String, Double> preciosSombra,
        RangosSensibilidad rangosSensibilidad
) {}
