package jpap.dev.io_api.domain.entera;

import java.util.Map;

/**
 * Solución óptima de un modelo de Programación Lineal Entera.
 *
 * @param valores          valores óptimos por variable (enteros, expresados como Double por convención con SolucionLP)
 * @param valorOptimo      valor óptimo entero Z* de la función objetivo
 * @param valorRelajacion  valor óptimo de la relajación LP en la raíz (cota superior en MAX / inferior en MIN);
 *                         la diferencia con {@code valorOptimo} es la <em>brecha de integralidad</em> y explica
 *                         por qué redondear la relajación no es válido
 * @param nodosExplorados  número de nodos (subproblemas LP) explorados por Branch &amp; Bound
 */
public record SolucionEntera(
        Map<String, Double> valores,
        double valorOptimo,
        double valorRelajacion,
        int nodosExplorados
) {}
