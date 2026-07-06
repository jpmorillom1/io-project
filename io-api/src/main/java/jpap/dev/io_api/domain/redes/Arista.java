package jpap.dev.io_api.domain.redes;

/**
 * Arista/arco del grafo. Los campos numéricos son opcionales y se usan según el método:
 *   - peso:      Dijkstra y Kruskal
 *   - capacidad: Edmonds-Karp y Flujo de Costo Mínimo
 *   - costo:     Flujo de Costo Mínimo (y las aristas internas de Asignación)
 */
public record Arista(
        String origen,
        String destino,
        Double peso,
        Double capacidad,
        Double costo
) {}
