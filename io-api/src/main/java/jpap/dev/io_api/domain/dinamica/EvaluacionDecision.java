package jpap.dev.io_api.domain.dinamica;

/**
 * Una celda de la tabla de solución: qué pasa si en este estado se toma esta decisión.
 *
 * Es el desglose que exige la función de recurrencia: contribución inmediata de la
 * decisión más el valor óptimo ya calculado de la etapa siguiente.
 *
 * @param decision     etiqueta de la decisión evaluada ("x = 2", "CONSERVAR", "ir a C")
 * @param contribucion retorno o costo inmediato de la decisión en esta etapa
 * @param valorFuturo  valor óptimo de la etapa siguiente en el estado al que lleva la decisión
 * @param valorTotal   contribucion + valorFuturo
 * @param optima       true si esta decisión es la que alcanza el óptimo de la fila
 */
public record EvaluacionDecision(
        String decision,
        double contribucion,
        double valorFuturo,
        double valorTotal,
        boolean optima
) {}
