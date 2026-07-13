package jpap.dev.io_api.domain.dinamica;

import java.util.List;

/**
 * Una columna de la red por etapas del modelo RUTA_ETAPAS: el conjunto de nodos (estados)
 * alcanzables en esa etapa. La primera etapa contiene el origen y la última el destino.
 *
 * @param etapa número de etapa, empezando en 1
 * @param nodos nombres de los nodos disponibles en esa etapa
 */
public record EtapaRuta(
        int etapa,
        List<String> nodos
) {}
