package jpap.dev.io_api.domain.dinamica;

/**
 * Un eslabón de la política óptima, recuperada hacia adelante desde el estado inicial.
 * La lista completa es la respuesta a "qué hago en cada etapa".
 *
 * @param etapa         número de etapa
 * @param nombreEtapa   etiqueta legible de la etapa
 * @param estadoEntrada estado con el que se llega a la etapa
 * @param decision      decisión óptima que se toma
 * @param contribucion  retorno o costo inmediato de esa decisión
 * @param estadoSalida  estado con el que se pasa a la etapa siguiente
 */
public record DecisionOptima(
        int etapa,
        String nombreEtapa,
        String estadoEntrada,
        String decision,
        double contribucion,
        String estadoSalida
) {}
