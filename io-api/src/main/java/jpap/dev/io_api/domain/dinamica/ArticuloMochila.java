package jpap.dev.io_api.domain.dinamica;

/**
 * Un artículo (proyecto, inversión, ítem) candidato del modelo MOCHILA. Es una etapa.
 *
 * @param nombre          etiqueta legible
 * @param peso            unidades de recurso que consume cada unidad del artículo (entero positivo)
 * @param valor           beneficio que aporta cada unidad del artículo
 * @param unidadesMaximas cuántas unidades como máximo se pueden llevar; null equivale a 1,
 *                        es decir el caso clásico 0/1 (llevar el artículo o no)
 */
public record ArticuloMochila(
        String nombre,
        int peso,
        double valor,
        Integer unidadesMaximas
) {

    /** Unidades máximas efectivas: el valor dado o 1 (mochila 0/1). */
    public int unidadesMaximasOrDefault() {
        return unidadesMaximas != null ? unidadesMaximas : 1;
    }
}
