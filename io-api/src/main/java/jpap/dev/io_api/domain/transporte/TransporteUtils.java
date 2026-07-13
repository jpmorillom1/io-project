package jpap.dev.io_api.domain.transporte;

import jpap.dev.io_api.domain.transporte.Balanceador.Balanceado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidades compartidas por los solvers de transporte: cálculo de costo,
 * conversión a estructuras serializables y armado del mapa `datos` de cada paso.
 *
 * Convención del mapa de un paso (lo consume la UI para pintar la matriz):
 *   tipo, origenes, destinos, costos, oferta, demanda, asignaciones
 * donde `asignaciones` usa null para las celdas NO básicas (rutas sin uso) y el
 * valor enviado para las básicas (incluye 0 en celdas básicas degeneradas).
 */
public final class TransporteUtils {

    private static final double EPSILON = 1e-9;

    private TransporteUtils() {}

    public static double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Costo total = Σ costos[i][j] · x[i][j]. */
    public static double costoTotal(double[][] costos, double[][] x) {
        double total = 0.0;
        for (int i = 0; i < x.length; i++)
            for (int j = 0; j < x[i].length; j++)
                total += costos[i][j] * x[i][j];
        return round(total);
    }

    public static List<Double> arr(double[] a) {
        List<Double> out = new ArrayList<>(a.length);
        for (double v : a) out.add(round(v));
        return out;
    }

    public static List<List<Double>> mat(double[][] m) {
        List<List<Double>> out = new ArrayList<>(m.length);
        for (double[] fila : m) out.add(arr(fila));
        return out;
    }

    /** Matriz de asignaciones con null en celdas NO básicas (para mostrar la estructura de la BFS). */
    public static List<List<Double>> snapshot(double[][] x, boolean[][] basica) {
        List<List<Double>> out = new ArrayList<>(x.length);
        for (int i = 0; i < x.length; i++) {
            List<Double> fila = new ArrayList<>(x[i].length);
            for (int j = 0; j < x[i].length; j++) {
                fila.add(basica[i][j] ? round(x[i][j]) : null);
            }
            out.add(fila);
        }
        return out;
    }

    /** Matriz de asignaciones final: cantidad enviada en cada ruta (0 si no se usa). */
    public static List<List<Double>> asignacionesFinales(double[][] x) {
        return mat(x);
    }

    /** Mapa `datos` común a todos los pasos de transporte, con la instantánea de asignaciones. */
    public static Map<String, Object> datosBase(Balanceado b, double[][] x, boolean[][] basica) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "TRANSPORTE");
        datos.put("origenes", b.origenes());
        datos.put("destinos", b.destinos());
        datos.put("costos", mat(b.costos()));
        datos.put("oferta", arr(b.oferta()));
        datos.put("demanda", arr(b.demanda()));
        datos.put("asignaciones", snapshot(x, basica));
        return datos;
    }

    /** Texto que explica si hubo balanceo (origen/destino ficticio) o no. */
    public static String descripcionBalanceo(Balanceado b) {
        if (b.origenFicticio())
            return "Σoferta < Σdemanda: se agregó un ORIGEN ficticio con costo 0 que absorbe la demanda no atendida.";
        if (b.destinoFicticio())
            return "Σoferta > Σdemanda: se agregó un DESTINO ficticio con costo 0 que absorbe la oferta sobrante.";
        return "El problema ya está balanceado (Σoferta = Σdemanda): no se requiere origen ni destino ficticio.";
    }

    /** Número de celdas básicas (asignadas), para diagnosticar degeneración (esperado m+n−1). */
    public static int celdasBasicas(boolean[][] basica) {
        int c = 0;
        for (boolean[] fila : basica)
            for (boolean v : fila)
                if (v) c++;
        return c;
    }

    /** Construye la solución final a partir del modelo balanceado y la matriz de asignaciones. */
    public static SolucionTransporte solucion(Balanceado b, double[][] x,
                                              List<CostoPorMetodo> comparativaInicial,
                                              MetodoTransporte metodoInicial) {
        return new SolucionTransporte(
                b.origenes(),
                b.destinos(),
                asignacionesFinales(x),
                costoTotal(b.costos(), x),
                comparativaInicial,
                metodoInicial);
    }
}
