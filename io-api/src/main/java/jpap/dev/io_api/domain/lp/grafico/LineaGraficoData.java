package jpap.dev.io_api.domain.lp.grafico;

import java.util.List;
import java.util.Map;

public record LineaGraficoData(
        int indice,
        String etiqueta,
        String tipo,
        List<Map<String, Double>> puntos
) {}
