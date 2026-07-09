package jpap.dev.io_api.domain.dinamica;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidades compartidas por los solvers de Programación Dinámica: redondeo, comparación
 * según el sentido de optimización y armado del mapa {@code datos} de cada paso.
 *
 * Convención del mapa de un paso (lo consume la UI para mostrar el procedimiento):
 *   tipo="PROGRAMACION_DINAMICA", metodo, y luego según el paso:
 *     - paso de formulación: etapas, estados, decisiones, recurrencia, principioOptimalidad
 *     - paso de etapa:       tabla (etapa, nombreEtapa, recurrencia, filas)
 *     - paso final:          politica, valorOptimo, rutaOptima (si aplica)
 *
 * IMPORTANTE: el valor centinela de "estado inalcanzable" es infinito. Nunca debe llegar a
 * un {@link FilaEtapa} ni a {@link SolucionDinamica}: Jackson lo serializaría como el token
 * {@code Infinity}, que no es JSON válido. Los solvers omiten esos estados de la tabla.
 */
public final class DinamicaUtils {

    private static final double EPSILON = 1e-9;

    /** Texto del principio de optimalidad de Bellman, común a los cinco submodelos. */
    public static final String PRINCIPIO_OPTIMALIDAD =
            "Principio de optimalidad de Bellman: cualquiera que sea el estado con el que se llega a "
            + "una etapa, las decisiones que restan deben formar una política óptima para el subproblema "
            + "que arranca en ese estado. Por eso la recursión hacia atrás es válida: al resolver una "
            + "etapa ya se conoce el valor óptimo de todas las etapas posteriores, y basta comparar la "
            + "contribución inmediata de cada decisión contra ese valor futuro ya optimizado.";

    private DinamicaUtils() {}

    public static double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Redondeo a 2 decimales para textos legibles. */
    public static double round2(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }

    /** Formatea un número para los textos de los pasos: sin ".0" cuando es entero. */
    public static String fmt(double v) {
        double r = round2(v);
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    /** Valor centinela de "estado inalcanzable": el peor posible según el sentido. */
    public static double peorValor(SentidoOptimizacion sentido) {
        return sentido == SentidoOptimizacion.MAXIMIZAR
                ? Double.NEGATIVE_INFINITY
                : Double.POSITIVE_INFINITY;
    }

    public static boolean inalcanzable(double valor) {
        return Double.isInfinite(valor);
    }

    /** true si {@code candidato} es estrictamente mejor que {@code actual} según el sentido. */
    public static boolean esMejor(SentidoOptimizacion sentido, double candidato, double actual) {
        return sentido == SentidoOptimizacion.MAXIMIZAR
                ? candidato > actual + EPSILON
                : candidato < actual - EPSILON;
    }

    /** Mapa {@code datos} base de un paso de PD. */
    public static Map<String, Object> datos(MetodoDinamico metodo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "PROGRAMACION_DINAMICA");
        datos.put("metodo", metodo.name());
        return datos;
    }

    /** Mapa {@code datos} del paso 1: la formulación del modelo. */
    public static Map<String, Object> datosFormulacion(MetodoDinamico metodo, String etapas, String estados,
                                                       String decisiones, String recurrencia) {
        Map<String, Object> datos = datos(metodo);
        datos.put("etapas", etapas);
        datos.put("estados", estados);
        datos.put("decisiones", decisiones);
        datos.put("recurrencia", recurrencia);
        datos.put("principioOptimalidad", PRINCIPIO_OPTIMALIDAD);
        return datos;
    }

    /** Mapa {@code datos} de un paso de etapa: la tabla de solución de esa etapa. */
    public static Map<String, Object> datosTabla(MetodoDinamico metodo, TablaEtapa tabla) {
        Map<String, Object> datos = datos(metodo);
        datos.put("tabla", tabla);
        return datos;
    }

    /** Mapa {@code datos} del paso final: la política óptima recuperada hacia adelante. */
    public static Map<String, Object> datosPolitica(MetodoDinamico metodo, List<DecisionOptima> politica,
                                                    double valorOptimo, List<String> rutaOptima) {
        Map<String, Object> datos = datos(metodo);
        datos.put("politica", politica);
        datos.put("valorOptimo", round(valorOptimo));
        if (rutaOptima != null) datos.put("rutaOptima", rutaOptima);
        return datos;
    }
}
