package jpap.dev.io_api.domain.transporte.vogel;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.transporte.Balanceador;
import jpap.dev.io_api.domain.transporte.Balanceador.Balanceado;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import jpap.dev.io_api.domain.transporte.TransporteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aproximación de Vogel (VAM): en cada paso calcula, por cada fila y columna activa,
 * la penalización = diferencia entre los dos costos más bajos. Elige la línea de mayor
 * penalización (la que "más pierde" si no se atiende su celda barata) y asigna en su
 * celda de menor costo. Suele producir la mejor solución inicial de los tres métodos.
 *
 * Java puro, sin dependencias de framework.
 */
public class VogelSolver {

    private static final double EPS = 1e-9;
    private static final double INF = Double.MAX_VALUE;

    public SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo) {
        Balanceado b = Balanceador.balancear(modelo);
        int fil = b.oferta().length;
        int col = b.demanda().length;

        double[][] x = new double[fil][col];
        boolean[][] basica = new boolean[fil][col];

        List<SolveStep> pasos = new ArrayList<>();
        Map<String, Object> datos0 = TransporteUtils.datosBase(b, x, basica);
        pasos.add(new SolveStep(0, "Balanceo y tabla inicial (Vogel / VAM)",
                TransporteUtils.descripcionBalanceo(b), datos0));

        asignar(b, x, basica, pasos);

        Map<String, Object> datosF = TransporteUtils.datosBase(b, x, basica);
        datosF.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        datosF.put("celdasBasicas", TransporteUtils.celdasBasicas(basica));
        pasos.add(new SolveStep(pasos.size(), "Solución básica inicial completa",
                "Todas las ofertas y demandas quedaron cubiertas. Costo de esta solución inicial = "
                        + TransporteUtils.costoTotal(b.costos(), x)
                        + ". Vogel suele quedar cerca del óptimo; MODI lo confirma.",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                TransporteUtils.solucion(b, x, null, null), pasos);
    }

    static void asignar(Balanceado b, double[][] x, boolean[][] basica, List<SolveStep> pasos) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        double[][] c = b.costos();
        double[] of = b.oferta().clone();
        double[] de = b.demanda().clone();
        boolean[] filaLista = new boolean[fil];
        boolean[] colLista = new boolean[col];

        while (disponibles(filaLista) > 0 && disponibles(colLista) > 0) {
            double[] penalFila = new double[fil];
            double[] penalCol = new double[col];
            for (int i = 0; i < fil; i++)
                penalFila[i] = filaLista[i] ? -1 : penalizacionFila(c, i, colLista, col);
            for (int j = 0; j < col; j++)
                penalCol[j] = colLista[j] ? -1 : penalizacionColumna(c, j, filaLista, fil);

            // Línea de mayor penalización (empate: primero fila, luego columna, por índice)
            boolean esFila = true;
            int linea = -1;
            double mejor = -1;
            for (int i = 0; i < fil; i++)
                if (penalFila[i] > mejor + EPS) { mejor = penalFila[i]; linea = i; esFila = true; }
            for (int j = 0; j < col; j++)
                if (penalCol[j] > mejor + EPS) { mejor = penalCol[j]; linea = j; esFila = false; }

            // Celda de menor costo dentro de la línea elegida
            int ci, cj;
            if (esFila) {
                ci = linea;
                cj = minCostoEnFila(c, ci, colLista, col);
            } else {
                cj = linea;
                ci = minCostoEnColumna(c, cj, filaLista, fil);
            }

            double cant = Math.min(of[ci], de[cj]);
            x[ci][cj] += cant;
            basica[ci][cj] = true;
            of[ci] -= cant;
            de[cj] -= cant;

            if (pasos != null) {
                Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
                datos.put("penalizacionesFila", penalizacionesToList(penalFila, filaLista));
                datos.put("penalizacionesColumna", penalizacionesToList(penalCol, colLista));
                datos.put("celda", new int[]{ci, cj});
                datos.put("cantidad", TransporteUtils.round(cant));
                datos.put("lineaElegida", (esFila ? "fila " : "columna ")
                        + (esFila ? b.origenes().get(ci) : b.destinos().get(cj)));
                pasos.add(new SolveStep(pasos.size(),
                        "Penalización máxima " + TransporteUtils.round(mejor) + " → asignar "
                                + TransporteUtils.round(cant) + " a (" + b.origenes().get(ci)
                                + " → " + b.destinos().get(cj) + ")",
                        "La " + (esFila ? "fila " + b.origenes().get(ci) : "columna " + b.destinos().get(cj))
                                + " tiene la mayor penalización. Dentro de ella, la celda más barata es ("
                                + b.origenes().get(ci) + " → " + b.destinos().get(cj) + ") con costo "
                                + TransporteUtils.round(c[ci][cj]) + ".",
                        datos));
            }

            if (of[ci] <= EPS && de[cj] <= EPS) {
                if (disponibles(filaLista) > 1) filaLista[ci] = true; else colLista[cj] = true;
            } else if (of[ci] <= EPS) {
                filaLista[ci] = true;
            } else {
                colLista[cj] = true;
            }
        }
    }

    // ─── penalizaciones ──────────────────────────────────────────────────────

    private static double penalizacionFila(double[][] c, int i, boolean[] colLista, int col) {
        double min1 = INF, min2 = INF;
        for (int j = 0; j < col; j++) {
            if (colLista[j]) continue;
            double v = c[i][j];
            if (v < min1) { min2 = min1; min1 = v; }
            else if (v < min2) { min2 = v; }
        }
        if (min1 == INF) return -1;          // fila sin columnas activas
        return (min2 == INF) ? min1 : min2 - min1;
    }

    private static double penalizacionColumna(double[][] c, int j, boolean[] filaLista, int fil) {
        double min1 = INF, min2 = INF;
        for (int i = 0; i < fil; i++) {
            if (filaLista[i]) continue;
            double v = c[i][j];
            if (v < min1) { min2 = min1; min1 = v; }
            else if (v < min2) { min2 = v; }
        }
        if (min1 == INF) return -1;
        return (min2 == INF) ? min1 : min2 - min1;
    }

    private static int minCostoEnFila(double[][] c, int i, boolean[] colLista, int col) {
        int arg = -1;
        double min = INF;
        for (int j = 0; j < col; j++) {
            if (colLista[j]) continue;
            if (c[i][j] < min - EPS) { min = c[i][j]; arg = j; }
        }
        return arg;
    }

    private static int minCostoEnColumna(double[][] c, int j, boolean[] filaLista, int fil) {
        int arg = -1;
        double min = INF;
        for (int i = 0; i < fil; i++) {
            if (filaLista[i]) continue;
            if (c[i][j] < min - EPS) { min = c[i][j]; arg = i; }
        }
        return arg;
    }

    private static List<Double> penalizacionesToList(double[] penal, boolean[] listas) {
        List<Double> out = new ArrayList<>(penal.length);
        for (int k = 0; k < penal.length; k++)
            out.add(listas[k] ? null : TransporteUtils.round(penal[k]));
        return out;
    }

    private static int disponibles(boolean[] listas) {
        int c = 0;
        for (boolean l : listas) if (!l) c++;
        return c;
    }

    /** Matriz de asignación inicial (sin pasos), reutilizada por MODI. */
    public static double[][] construir(Balanceado b) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        double[][] x = new double[fil][col];
        boolean[][] basica = new boolean[fil][col];
        asignar(b, x, basica, null);
        return x;
    }
}
