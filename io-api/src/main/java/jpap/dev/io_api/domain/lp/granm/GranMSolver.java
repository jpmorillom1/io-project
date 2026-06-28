package jpap.dev.io_api.domain.lp.granm;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
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
 * Método Gran M (penalidad) para PL con restricciones >=, = y <=.
 * Introduce variables artificiales con penalidad M en la función objetivo
 * para forzar su salida de la base. Java puro, sin Spring.
 *
 * Convención z-row (igual que SimplexSolver):
 *   MAX z = c^T x  → almacena -c (óptimo cuando todo >= 0)
 *   MIN z = c^T x  → almacena +c (w = -z, revertir al final)
 * Artificiales: siempre +M en la fila z (penalidad en ambos sentidos).
 */
public class GranMSolver {

    private static final double EPSILON = 1e-9;
    private static final int MAX_ITER = 1000;
    private static final double M = 1_000_000.0;

    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        validar(modelo);

        int n = modelo.variables().size();
        int m = modelo.restricciones().size();
        boolean isMin = modelo.objetivo().tipo() == TipoObjetivo.MINIMIZAR;

        // Contar columnas por tipo de restricción
        int nSlack = 0, nArt = 0;
        for (Restriccion r : modelo.restricciones()) {
            if (r.tipo() != TipoRestriccion.EQ) nSlack++;
            if (r.tipo() != TipoRestriccion.LEQ) nArt++;
        }
        int totalVars = n + nSlack + nArt;
        int cols = totalVars + 1;

        // Asignar columnas a cada restricción
        int[] slackCol = new int[m];
        int[] artCol   = new int[m];
        int[] base     = new int[m];
        int slackIdx = 0, artIdx = 0;
        for (int i = 0; i < m; i++) {
            TipoRestriccion tipo = modelo.restricciones().get(i).tipo();
            slackCol[i] = (tipo != TipoRestriccion.EQ)  ? n + slackIdx++           : -1;
            artCol[i]   = (tipo != TipoRestriccion.LEQ) ? n + nSlack + artIdx++    : -1;
            base[i]     = (artCol[i] >= 0) ? artCol[i] : slackCol[i];
        }

        List<String> headers = buildHeaders(modelo.variables(), modelo.restricciones());
        double[][] t = buildTableau(modelo, n, m, cols, slackCol, artCol, isMin);

        // Eliminar artificiales de la fila z (son básicos con valor M)
        for (int i = 0; i < m; i++) {
            if (artCol[i] >= 0) {
                double factor = t[m][artCol[i]]; // = M
                for (int j = 0; j < cols; j++) t[m][j] -= factor * t[i][j];
            }
        }

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(buildStep(0,
                "Tableau inicial (Gran M)",
                "Variables artificiales introducidas con penalidad M=" + (long) M +
                        ". Se agregaron " + nSlack + " variable(s) de holgura/superávit y " +
                        nArt + " variable(s) artificial(es).",
                t, base, headers, null));

        // Iteraciones Simplex estándar
        for (int iter = 1; iter <= MAX_ITER; iter++) {
            int enterCol = findEnterCol(t, m, cols);
            if (enterCol < 0) break;

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

        // Verificar factibilidad: si alguna artificial sigue en la base con valor > 0
        Set<Integer> artColSet = new HashSet<>();
        for (int i = 0; i < m; i++) {
            if (artCol[i] >= 0) artColSet.add(artCol[i]);
        }
        for (int i = 0; i < m; i++) {
            if (artColSet.contains(base[i]) && t[i][cols - 1] > EPSILON) {
                pasos.add(buildStep(pasos.size(),
                        "Problema infactible",
                        "La variable artificial " + headers.get(base[i]) +
                                " permanece en la base con valor " + round(t[i][cols - 1]) +
                                " > 0. El sistema de restricciones no tiene solución factible.",
                        t, base, headers, Map.of("status", SolveStatus.INFACTIBLE.name())));
                return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
            }
        }

        return buildResult(t, base, headers, modelo, n, nSlack, cols, isMin, pasos);
    }

    // ─────────────────────── construcción del tableau ───────────────────────

    private double[][] buildTableau(ModeloLP modelo, int n, int m, int cols,
                                     int[] slackCol, int[] artCol, boolean isMin) {
        double[][] t = new double[m + 1][cols];

        for (int i = 0; i < m; i++) {
            Restriccion r = modelo.restricciones().get(i);
            for (int j = 0; j < n; j++) t[i][j] = r.coeficientes().get(j);
            if (slackCol[i] >= 0) {
                // LEQ → holgura +1; GEQ → superávit -1
                t[i][slackCol[i]] = (r.tipo() == TipoRestriccion.GEQ) ? -1.0 : 1.0;
            }
            if (artCol[i] >= 0) t[i][artCol[i]] = 1.0;
            t[i][cols - 1] = r.rhs();
        }

        // Fila z: variables de decisión
        for (int j = 0; j < n; j++) {
            double c = modelo.objetivo().coeficientes().get(j);
            t[m][j] = isMin ? c : -c;
        }
        // Penalidad M para artificiales (igual para MAX y MIN)
        for (int i = 0; i < m; i++) {
            if (artCol[i] >= 0) t[m][artCol[i]] = M;
        }
        return t;
    }

    private List<String> buildHeaders(List<String> vars, List<Restriccion> restricciones) {
        List<String> h = new ArrayList<>(vars);
        int sIdx = 0;
        for (Restriccion r : restricciones) {
            if (r.tipo() != TipoRestriccion.EQ) h.add("s" + (++sIdx));
        }
        int aIdx = 0;
        for (Restriccion r : restricciones) {
            if (r.tipo() != TipoRestriccion.LEQ) h.add("a" + (++aIdx));
        }
        return h;
    }

    // ─────────────────────── selección de pivote ────────────────────────────

    private int findEnterCol(double[][] t, int m, int cols) {
        int col = -1;
        double minVal = -EPSILON;
        for (int j = 0; j < cols - 1; j++) {
            if (t[m][j] < minVal) { minVal = t[m][j]; col = j; }
        }
        return col;
    }

    private int findLeaveRow(double[][] t, int enterCol, int m, int cols) {
        int row = -1;
        double minRatio = Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            if (t[i][enterCol] > EPSILON) {
                double ratio = t[i][cols - 1] / t[i][enterCol];
                if (ratio < minRatio - EPSILON) { minRatio = ratio; row = i; }
            }
        }
        return row;
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
                                                 ModeloLP modelo, int n, int nSlack, int cols,
                                                 boolean isMin, List<SolveStep> pasos) {
        double[] solArr = new double[headers.size()];
        for (int i = 0; i < base.length; i++) solArr[base[i]] = t[i][cols - 1];

        Map<String, Double> valores = new LinkedHashMap<>();
        for (int j = 0; j < n; j++) {
            valores.put(modelo.variables().get(j), round(solArr[j]));
        }

        double zOpt = t[base.length][cols - 1];
        if (isMin) zOpt = -zOpt;

        // Óptimos múltiples: revisar solo vars de decisión y holgura (excluir artificiales)
        SolveStatus status = hasMultipleOptima(t, base, base.length, n, nSlack)
                ? SolveStatus.MULTIPLE_OPTIMO
                : SolveStatus.OPTIMO;

        double zFinal = round(zOpt);
        pasos.add(buildStep(pasos.size(),
                "Solución óptima encontrada (Gran M)",
                "No quedan coeficientes negativos en la fila z. Valor óptimo Z* = " + zFinal + ".",
                t, base, headers,
                Map.of("valorOptimo", zFinal, "status", status.name())));

        return new SolveResult<>(status, new SolucionLP(valores, zFinal), pasos);
    }

    private boolean hasMultipleOptima(double[][] t, int[] base, int m, int n, int nSlack) {
        Set<Integer> basicSet = new HashSet<>();
        for (int b : base) basicSet.add(b);
        for (int j = 0; j < n + nSlack; j++) {
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
            if (r.rhs() < -EPSILON)
                throw new IllegalArgumentException(
                        "Restricción " + (i + 1) + " tiene b=" + r.rhs() +
                        ". Gran M requiere b >= 0.");
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
