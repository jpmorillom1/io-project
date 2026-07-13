package jpap.dev.io_api.domain.lp.simplex;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.RangosSensibilidad;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SensibilidadCalculator;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simplex estándar (forma canónica con variables de holgura).
 * Solo admite restricciones <= con b >= 0. Java puro, sin dependencias de framework.
 *
 * Convención de la fila-z:
 *   MAX z = c^T x  →  almacenamos -c en la fila z (óptimo cuando todo >= 0)
 *   MIN z = c^T x  →  convertimos a MAX w = -z: almacenamos +c (w* = -z*)
 */
public class SimplexSolver {

    private static final double EPSILON = 1e-9;
    private static final int MAX_ITER = 1000;

    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        validar(modelo);

        int n = modelo.variables().size();
        int m = modelo.restricciones().size();
        int cols = n + m + 1;   // vars originales + holguras + b
        boolean isMin = modelo.objetivo().tipo() == TipoObjetivo.MINIMIZAR;

        double[][] t = buildTableau(modelo, n, m, cols, isMin);
        int[] base = inicializarBase(n, m);
        List<String> headers = buildHeaders(modelo.variables(), m);

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(buildStep(0,
                "Tableau inicial",
                "Se agregan " + m + " variable(s) de holgura (s1..s" + m +
                        "). La base inicial son las holguras con solución básica factible x=0.",
                t, base, headers, null));

        for (int iter = 1; iter <= MAX_ITER; iter++) {

            int enterCol = findEnterCol(t, m, cols);
            if (enterCol < 0) break; // óptimo alcanzado

            int leaveRow = findLeaveRow(t, enterCol, m, cols);
            if (leaveRow < 0) {
                return new SolveResult<>(SolveStatus.NO_ACOTADO, null, pasos);
            }

            String entering = headers.get(enterCol);
            String leaving  = headers.get(base[leaveRow]);

            pivot(t, leaveRow, enterCol, m, cols);
            base[leaveRow] = enterCol;

            pasos.add(buildStep(iter,
                    "Iteración " + iter + ": entra " + entering + ", sale " + leaving,
                    "Pivote en fila " + (leaveRow + 1) + ", columna '" + entering +
                            "'. La variable '" + leaving + "' sale de la base.",
                    t, base, headers,
                    Map.of("varEntra", entering, "varSale", leaving)));
        }

        return buildResult(t, base, headers, modelo, n, m, cols, isMin, pasos);
    }

    // ─────────────────────── construcción del tableau ───────────────────────

    private double[][] buildTableau(ModeloLP modelo, int n, int m, int cols, boolean isMin) {
        double[][] t = new double[m + 1][cols];

        for (int i = 0; i < m; i++) {
            Restriccion r = modelo.restricciones().get(i);
            for (int j = 0; j < n; j++) t[i][j] = r.coeficientes().get(j);
            t[i][n + i]     = 1.0;        // columna identidad de la holgura
            t[i][cols - 1]  = r.rhs();    // término independiente
        }

        // Fila z: MAX almacena -c; MIN (convertido a MAX -z) almacena +c
        for (int j = 0; j < n; j++) {
            double c = modelo.objetivo().coeficientes().get(j);
            t[m][j] = isMin ? c : -c;
        }
        return t;
    }

    private int[] inicializarBase(int n, int m) {
        int[] base = new int[m];
        for (int i = 0; i < m; i++) base[i] = n + i;
        return base;
    }

    private List<String> buildHeaders(List<String> vars, int m) {
        List<String> h = new ArrayList<>(vars);
        for (int i = 1; i <= m; i++) h.add("s" + i);
        return h; // n+m entradas; "b" se agrega al armar el paso
    }

    // ─────────────────────── selección de pivote ────────────────────────────

    /** Columna de entrada: coeficiente más negativo en la fila z (regla de Dantzig). */
    private int findEnterCol(double[][] t, int m, int cols) {
        int col = -1;
        double minVal = -EPSILON;
        for (int j = 0; j < cols - 1; j++) {
            if (t[m][j] < minVal) { minVal = t[m][j]; col = j; }
        }
        return col;
    }

    /** Fila de salida: prueba de razón mínima (evita ciclos con tie-breaking por índice). */
    private int findLeaveRow(double[][] t, int enterCol, int m, int cols) {
        int row = -1;
        double minRatio = Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            if (t[i][enterCol] > EPSILON) {
                double ratio = t[i][cols - 1] / t[i][enterCol];
                if (ratio < minRatio - EPSILON) { minRatio = ratio; row = i; }
            }
        }
        return row; // -1 → no acotado
    }

    // ─────────────────────── pivoteo ────────────────────────────────────────

    private void pivot(double[][] t, int pRow, int pCol, int m, int cols) {
        double elem = t[pRow][pCol];
        for (int j = 0; j < cols; j++) t[pRow][j] /= elem;

        for (int i = 0; i <= m; i++) {
            if (i == pRow) continue;
            double factor = t[i][pCol];
            if (Math.abs(factor) > EPSILON) {
                for (int j = 0; j < cols; j++) t[i][j] -= factor * t[pRow][j];
            }
        }
    }

    // ─────────────────────── resultado final ────────────────────────────────

    private SolveResult<SolucionLP> buildResult(double[][] t, int[] base, List<String> headers,
                                                 ModeloLP modelo, int n, int m, int cols,
                                                 boolean isMin, List<SolveStep> pasos) {
        double[] solArr = new double[n + m];
        for (int i = 0; i < m; i++) solArr[base[i]] = t[i][cols - 1];

        Map<String, Double> valores = new LinkedHashMap<>();
        for (int j = 0; j < n; j++) {
            valores.put(modelo.variables().get(j), round(solArr[j]));
        }

        // slackCol[i] = n + i para SimplexSolver (todas LEQ, holguras secuenciales)
        int[] slackCol = new int[m];
        for (int i = 0; i < m; i++) slackCol[i] = n + i;

        Map<String, Double> holguras       = SensibilidadCalculator.calcularHolguras(solArr, m, slackCol);
        Map<String, Double> preciosSombra  = SensibilidadCalculator.calcularPreciosSombra(t, base, modelo, n, m, slackCol, null);
        RangosSensibilidad  rangos         = SensibilidadCalculator.calcularRangos(t, base, modelo, n, m, cols, n + m, slackCol, null, isMin);

        double zOpt = t[m][cols - 1];
        if (isMin) zOpt = -zOpt;

        SolveStatus status = hasMultipleOptima(t, base, m, n + m)
                ? SolveStatus.MULTIPLE_OPTIMO
                : SolveStatus.OPTIMO;

        double zFinal = round(zOpt);
        pasos.add(buildStep(pasos.size(),
                "Solución óptima encontrada",
                "No quedan coeficientes negativos en la fila z. Valor óptimo Z* = " + zFinal + ".",
                t, base, headers,
                Map.of("valorOptimo", zFinal, "status", status.name())));

        return new SolveResult<>(status, new SolucionLP(valores, holguras, zFinal, preciosSombra, rangos), pasos);
    }

    private boolean hasMultipleOptima(double[][] t, int[] base, int m, int totalVars) {
        Set<Integer> basicSet = new HashSet<>();
        for (int b : base) basicSet.add(b);
        for (int j = 0; j < totalVars; j++) {
            if (!basicSet.contains(j) && Math.abs(t[m][j]) < EPSILON) return true;
        }
        return false;
    }

    // ─────────────────────── construcción de pasos ──────────────────────────

    private SolveStep buildStep(int num, String titulo, String desc,
                                double[][] t, int[] base, List<String> headers,
                                Map<String, Object> extra) {
        int m    = t.length - 1;
        int cols = t[0].length;

        // Copia profunda del tableau con valores redondeados
        double[][] copy = new double[m + 1][cols];
        for (int i = 0; i <= m; i++)
            for (int j = 0; j < cols; j++)
                copy[i][j] = round(t[i][j]);

        List<String> enc = new ArrayList<>(headers);
        enc.add("b");

        String[] baseLabels = new String[m];
        for (int i = 0; i < m; i++) baseLabels[i] = headers.get(base[i]);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("encabezados", enc);
        datos.put("tableau", copy);
        datos.put("base", baseLabels);
        if (extra != null) datos.putAll(extra);

        return new SolveStep(num, titulo, desc, datos);
    }

    // ─────────────────────── utilidades ─────────────────────────────────────

    private void validar(ModeloLP modelo) {
        if (modelo.variables() == null || modelo.variables().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos una variable.");
        int n = modelo.variables().size();
        if (modelo.objetivo() == null || modelo.objetivo().coeficientes().size() != n)
            throw new IllegalArgumentException("La función objetivo debe tener " + n + " coeficiente(s).");
        if (modelo.restricciones() == null || modelo.restricciones().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos una restricción.");
        for (int i = 0; i < modelo.restricciones().size(); i++) {
            Restriccion r = modelo.restricciones().get(i);
            if (r.tipo() != TipoRestriccion.LEQ)
                throw new IllegalArgumentException(
                        "Simplex estándar solo admite restricciones <=. Restricción " + (i + 1) +
                        " es de tipo " + r.tipo() + ". Usa Dos Fases o Gran M para >= o =.");
            if (r.rhs() < -EPSILON)
                throw new IllegalArgumentException(
                        "Restricción " + (i + 1) + " tiene b=" + r.rhs() +
                        ". Simplex estándar requiere b >= 0.");
            if (r.coeficientes() == null || r.coeficientes().size() != n)
                throw new IllegalArgumentException(
                        "Restricción " + (i + 1) + " debe tener " + n + " coeficiente(s).");
        }
    }

    private double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
