package jpap.dev.io_api.domain.lp;

import java.util.Map;

public record SolucionLP(
        Map<String, Double> valores,
        double valorOptimo
) {}
