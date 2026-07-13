package jpap.dev.io_api.domain.redes;

import java.util.List;
import java.util.Map;

/**
 * Solución unificada de los cinco problemas de redes. Los campos son nullable
 * según el método que la produjo:
 *
 *   - distancias:      DIJKSTRA — distancia mínima desde la fuente a cada nodo alcanzado
 *   - rutaOptima:      DIJKSTRA — secuencia de nodos fuente→sumidero (null si no se pidió sumidero)
 *   - aristasSolucion: KRUSKAL (árbol), DIJKSTRA (ruta/árbol), EK y MCF (arcos con flujo &gt; 0),
 *                      ASIGNACION (pares agente→tarea elegidos)
 *   - flujoPorArco:    EK / MCF / ASIGNACION — clave "origen-&gt;destino", valor flujo enviado
 *   - asignacion:      ASIGNACION — agente → tarea (excluye al agente/tarea ficticio del balanceo)
 *   - valorObjetivo:   distancia al sumidero / peso del árbol / flujo máximo /
 *                      costo total mínimo (MCF) / costo total de la asignación
 *   - flujoTotal:      EK / MCF / ASIGNACION — unidades totales enviadas fuente→sumidero
 *   - costoTotal:      MCF / ASIGNACION — costo total mínimo
 */
public record SolucionRed(
        Map<String, Double> distancias,
        List<String> rutaOptima,
        List<Arista> aristasSolucion,
        Map<String, Double> flujoPorArco,
        Map<String, String> asignacion,
        double valorObjetivo,
        Double flujoTotal,
        Double costoTotal
) {}
