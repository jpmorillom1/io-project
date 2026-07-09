package jpap.dev.io_api.domain.dinamica;

/**
 * Arco de la red por etapas: conecta un nodo de la etapa k con uno de la etapa k+1.
 *
 * @param origen  nodo de la etapa k
 * @param destino nodo de la etapa k+1
 * @param costo   contribución del arco (distancia, costo o beneficio según el sentido)
 */
public record ArcoRuta(
        String origen,
        String destino,
        double costo
) {}
