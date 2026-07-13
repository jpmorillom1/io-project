package jpap.dev.io_api.domain.redes;

import jpap.dev.io_api.domain.common.ModeloResoluble;

import java.util.List;

/**
 * Datos de entrada de un problema de redes. Los campos se usan según el método:
 *
 *   - DIJKSTRA:           nodos, aristas (peso), dirigido, fuente, sumidero (opcional:
 *                         si es null se calculan las distancias a todos los nodos)
 *   - KRUSKAL:            nodos, aristas (peso) — el grafo se trata como NO dirigido
 *   - EDMONDS_KARP:       nodos, aristas (capacidad), fuente, sumidero
 *   - FLUJO_COSTO_MINIMO: nodos, aristas (capacidad y costo), fuente, sumidero
 *   - ASIGNACION:         agentes, tareas y matrizCostos (los demás campos se ignoran;
 *                         el solver construye internamente la red bipartita)
 */
public record ModeloRed(
        List<String> nodos,
        List<Arista> aristas,
        boolean dirigido,
        MetodoRed metodo,
        String fuente,
        String sumidero,
        List<String> agentes,
        List<String> tareas,
        List<List<Double>> matrizCostos
) implements ModeloResoluble {

    /** Devuelve una copia con el método indicado (los controllers fuerzan el método por endpoint). */
    public ModeloRed conMetodo(MetodoRed nuevoMetodo) {
        return new ModeloRed(nodos, aristas, dirigido, nuevoMetodo, fuente, sumidero,
                agentes, tareas, matrizCostos);
    }
}
