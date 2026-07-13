package jpap.dev.io_api.domain.inventario;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades compartidas por los solvers de inventario: redondeo y armado del mapa
 * {@code datos} de cada paso de cálculo.
 *
 * Convención del mapa de un paso (lo consume la UI para mostrar el procedimiento):
 *   tipo="INVENTARIO", metodo, formula (expresión simbólica), sustitucion (con números),
 *   resultado (valor calculado). Los campos formula/sustitucion/resultado son opcionales
 *   por paso (un paso puede ser solo texto explicativo en su {@code descripcion}).
 */
public final class InventarioUtils {

    private static final double EPSILON = 1e-9;

    private InventarioUtils() {}

    public static double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Redondeo a 2 decimales para textos legibles (dinero, cantidades). */
    public static double round2(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }

    /** Mapa {@code datos} base de un paso de inventario. */
    public static Map<String, Object> datos(MetodoInventario metodo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "INVENTARIO");
        datos.put("metodo", metodo.name());
        return datos;
    }

    /**
     * Mapa {@code datos} de un paso de cálculo con fórmula simbólica, su sustitución
     * numérica y el resultado. Cualquiera de los strings puede ir null para omitirlo.
     */
    public static Map<String, Object> datosCalculo(MetodoInventario metodo, String formula,
                                                   String sustitucion, Double resultado) {
        Map<String, Object> datos = datos(metodo);
        if (formula != null) datos.put("formula", formula);
        if (sustitucion != null) datos.put("sustitucion", sustitucion);
        if (resultado != null) datos.put("resultado", round(resultado));
        return datos;
    }
}
