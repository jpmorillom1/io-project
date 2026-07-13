package jpap.dev.io_api.domain.redes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidades compartidas por los solvers de redes: redondeo, claves de arcos y
 * armado del mapa `datos` de cada paso.
 *
 * Convención del mapa de un paso (lo consume la UI para pintar el grafo):
 *   tipo="REDES", metodo, nodos, aristas, fuente?, sumidero?
 * donde `aristas` es una lista de mapas {origen, destino, peso?, capacidad?, costo?,
 * flujo?, estado} y `estado` marca cómo pintar la arista en ese paso:
 *   - "normal":     sin marca
 *   - "activa":     examinada/relajada/en el camino de aumento de ESTE paso
 *   - "solucion":   ya fijada en la solución parcial (árbol, ruta, arco con flujo)
 *   - "descartada": rechazada (p. ej. formaría ciclo en Kruskal)
 */
public final class RedUtils {

    private static final double EPSILON = 1e-9;

    public static final String ESTADO_NORMAL = "normal";
    public static final String ESTADO_ACTIVA = "activa";
    public static final String ESTADO_SOLUCION = "solucion";
    public static final String ESTADO_DESCARTADA = "descartada";

    private RedUtils() {}

    public static double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Clave estándar de un arco dirigido en flujoPorArco: "origen->destino". */
    public static String claveArco(String origen, String destino) {
        return origen + "->" + destino;
    }

    /** Representación serializable de una arista para el mapa `datos` de un paso. */
    public static Map<String, Object> arista(Arista a, String estado) {
        return arista(a, estado, null);
    }

    /** Igual que {@link #arista(Arista, String)} pero incluyendo el flujo actual del arco. */
    public static Map<String, Object> arista(Arista a, String estado, Double flujo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("origen", a.origen());
        m.put("destino", a.destino());
        if (a.peso() != null) m.put("peso", round(a.peso()));
        if (a.capacidad() != null) m.put("capacidad", round(a.capacidad()));
        if (a.costo() != null) m.put("costo", round(a.costo()));
        if (flujo != null) m.put("flujo", round(flujo));
        m.put("estado", estado != null ? estado : ESTADO_NORMAL);
        return m;
    }

    /** Mapa `datos` común a todos los pasos de redes, con las aristas ya serializadas. */
    public static Map<String, Object> datosBase(MetodoRed metodo, List<String> nodos,
                                                List<Map<String, Object>> aristas,
                                                String fuente, String sumidero) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "REDES");
        datos.put("metodo", metodo.name());
        datos.put("nodos", nodos);
        datos.put("aristas", aristas);
        if (fuente != null) datos.put("fuente", fuente);
        if (sumidero != null) datos.put("sumidero", sumidero);
        return datos;
    }

    /** datosBase del modelo tal cual, con todas las aristas en estado "normal". */
    public static Map<String, Object> datosBase(ModeloRed modelo) {
        List<Map<String, Object>> aristas = new ArrayList<>(modelo.aristas().size());
        for (Arista a : modelo.aristas()) aristas.add(arista(a, ESTADO_NORMAL));
        return datosBase(modelo.metodo(), modelo.nodos(), aristas, modelo.fuente(), modelo.sumidero());
    }
}
