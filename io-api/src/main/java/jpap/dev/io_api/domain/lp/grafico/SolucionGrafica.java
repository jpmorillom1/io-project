package jpap.dev.io_api.domain.lp.grafico;

import java.util.List;
import java.util.Map;

public record SolucionGrafica(
        Map<String, Double> valores,
        double valorOptimo,
        List<PuntoVertice> vertices,
        List<List<Double>> region,
        double xMax,
        double yMax
) {}
