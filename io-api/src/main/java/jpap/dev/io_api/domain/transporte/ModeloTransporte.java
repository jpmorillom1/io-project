package jpap.dev.io_api.domain.transporte;

import jpap.dev.io_api.domain.common.ModeloResoluble;

import java.util.List;

/**
 * Datos de entrada de un problema de transporte.
 *
 * La matriz de costos tiene una fila por origen y una columna por destino:
 *   costos.get(i).get(j) = costo unitario de enviar de origen i a destino j.
 *
 * El modelo puede estar desbalanceado (Σoferta ≠ Σdemanda); el Balanceador
 * agrega un origen/destino ficticio con costo 0 antes de resolver.
 */
public record ModeloTransporte(
        List<String> origenes,
        List<String> destinos,
        List<Double> oferta,
        List<Double> demanda,
        List<List<Double>> costos,
        MetodoTransporte metodo
) implements ModeloResoluble {

    /** Devuelve una copia con el método indicado (los controllers fuerzan el método por endpoint). */
    public ModeloTransporte conMetodo(MetodoTransporte nuevoMetodo) {
        return new ModeloTransporte(origenes, destinos, oferta, demanda, costos, nuevoMetodo);
    }
}
