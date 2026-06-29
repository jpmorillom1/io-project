package jpap.dev.io_api.domain.lp.dosfases;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Método Dos Fases para PL con restricciones >=, = y <=.
 *
 * Fase 1: minimiza w = Σ artificiales para encontrar una SBF.
 *         Si w* > 0 → el problema es infactible.
 * Fase 2: a partir de la SBF de Fase 1, optimiza el objetivo original.
 *
 * La numeración de pasos es continua entre Fase 1 y Fase 2 para que
 * la UI muestre la progresión completa.
 *
 * Convención z-row (igual que SimplexSolver):
 *   MAX → almacena -c; MIN → almacena +c.
 *   Fase 1 usa convención MIN (minimizar w = Σaᵢ → almacena +1 por artificial).
 */
public class DosFasesSolver {

    private static final double EPSILON = 1e-9;
    private static final int MAX_ITER = 1000;

    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        validar(modelo);

        int n = modelo.variables().size();
        int m = modelo.restricciones().size();
        boolean isMin = modelo.objetivo().tipo() == TipoObjetivo.MINIMIZAR;

        // Contar columnas
        int nSlack = 0, nArt = 0;
        for (Restriccion r : modelo.restricciones()) {
            if (r.tipo() != TipoRestriccion.EQ) nSlack++;
            if (r.tipo() != TipoRestriccion.LEQ) nArt++;
        }
        int totalVars = n + nSlack + nArt;
        int cols = totalVars + 1;

        // Asignar columnas
        int[] slackCol = new int[m];
        int[] artCol   = new int[m];
        int[] base     = new int[m];
        int slackIdx = 0, artIdx = 0;
        for (int i = 0; i < m; i++) {
            TipoRestriccion tipo = modelo.restricciones().get(i).tipo();
            slackCol[i] = (tipo != TipoRestriccion.EQ)  ? n + slackIdx++        : -1;
            artCol[i]   = (tipo != TipoRestriccion.LEQ) ? n + nSlack + artIdx++ : -1;
            base[i]     = (artCol[i] >= 0) ? artCol[i] : slackCol[i];
        }

        Set<Integer> artColSet = new HashSet<>();
        for (int i = 0; i < m; i++) { if (artCol[i] >= 0) artColSet.add(artCol[i]); }

        List<String> headers = buildHeaders(modelo.variables(), modelo.restricciones());
        double[][] t = buildTableau(modelo, n, m, cols, slackCol, artCol);

        List<SolveStep> pasos = new ArrayList<>();
        int stepNum = 0;

        // ── FASE 1 ──────────────────────────────────────────────────────────

        // Fila z Fase 1: MIN w = Σaᵢ → coeficiente +1 en columna de cada artificial
        Arrays.fill(t[m], 0.0);
        for (int i = 0; i < m; i++) {
            if (artCol[i] >= 0) t[m][artCol[i]] = 1.0;
        }

        // Eliminar básicos actuales (artificiales) de la fila z
        for (int i = 0; i < m; i++) {
            if (artCol[i] >= 0) {
                double factor = t[m][artCol[i]]; // = 1.0
                for (int j = 0; j < cols; j++) t[m][j] -= factor * t[i][j];
            }
        }

        pasos.add(buildStep(stepNum++,
                "Fase 1 — Tableau inicial (MIN w = Σ artificiales)",
                "Se construye el modelo auxiliar con " + nArt + " variable(s) artificial(es). " +
                        "La Fase 1 minimiza w = suma de artificiales para encontrar una solución básica factible.",
                t, base, headers, null));

        for (int iter = 1; iter <= MAX_ITER; iter++) {
            int enterCol = findEnterCol(t, m, cols);
            if (enterCol < 0) break;

            int leaveRow = findLeaveRow(t, enterCol, m, cols);
            if (leaveRow < 0) {
                // Fase 1 no acotada — no debería ocurrir con w = Σa >= 0, pero lo capturamos
                return new SolveResult<>(SolveStatus.NO_ACOTADO, null, pasos);
            }

            String entering = headers.get(enterCol);
            String leaving  = headers.get(base[leaveRow]);
            pivot(t, leaveRow, enterCol, m, cols);
            base[leaveRow] = enterCol;

            pasos.add(buildStep(stepNum++,
                    "Fase 1 — Iteración " + iter + ": entra " + entering + ", sale " + leaving,
                    "Pivote en fila " + (leaveRow + 1) + ", columna '" + entering +
                            "'. La variable '" + leaving + "' sale de la base.",
                    t, base, headers,
                    Map.of("varEntra", entering, "varSale", leaving)));
        }

        // w* está almacenado como -w* en la columna b de la fila z (convencion MIN)
        double wStar = -t[m][cols - 1];
        if (wStar > EPSILON) {
            pasos.add(buildStep(stepNum,
                    "Fase 1 infactible — w* = " + round(wStar),
                    "El valor mínimo de la función auxiliar w = " + round(wStar) +
                            " > 0. Esto significa que no existe ningún punto que satisfaga" +
                            " todas las restricciones simultáneamente.",
                    t, base, headers, Map.of("status", SolveStatus.INFACTIBLE.name())));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        pasos.add(buildStep(stepNum++,
                "Fase 1 completada — w* = 0",
                "Se encontró una solución básica factible con w* = 0. " +
                        "Todas las variables artificiales son cero. Pasamos a la Fase 2.",
                t, base, headers, null));

        // ── FASE 2 ──────────────────────────────────────────────────────────

        // Restaurar la fila z con el objetivo original
        Arrays.fill(t[m], 0.0);
        for (int j = 0; j < n; j++) {
            double c = modelo.objetivo().coeficientes().get(j);
            t[m][j] = isMin ? c : -c;
        }
        // Artificiales quedan con coeficiente 0 en z-row (no deben entrar en Fase 2)

        // Eliminar variables básicas actuales de la nueva fila z para consistencia
        for (int i = 0; i < m; i++) {
            double factor = t[m][base[i]];
            if (Math.abs(factor) > EPSILON) {
                for (int j = 0; j < cols; j++) t[m][j] -= factor * t[i][j];
            }
        }

        pasos.add(buildStep(stepNum++,
                "Fase 2 — Tableau inicial (objetivo original restituido)",
                "Se restaura la función objetivo original. La fila z se recalcula " +
                        "eliminando las variables básicas actuales para mantener la consistencia del tableau.",
                t, base, headers, null));

        for (int iter = 1; iter <= MAX_ITER; iter++) {
            int enterCol = findEnterColExcluding(t, m, cols, artColSet);
            if (enterCol < 0) break;

            int leaveRow = findLeaveRow(t, enterCol, m, cols);
            if (leaveRow < 0) {
                return new SolveResult<>(SolveStatus.NO_ACOTADO, null, pasos);
            }

            String entering = headers.get(enterCol);
            String leaving  = headers.get(base[leaveRow]);
            pivot(t, leaveRow, enterCol, m, cols);
            base[leaveRow] = enterCol;

            pasos.add(buildStep(stepNum++,
                    "Fase 2 — Iteración " + iter + ": entra " + entering + ", sale " + leaving,
                    "Pivote en fila " + (leaveRow + 1) + ", columna '" + entering +
                            "'. La variable '" + leaving + "' sale de la base.",
                    t, base, headers,
                    Map.of("varEntra", entering, "varSale", leaving)));
        }

        return buildResult(t, base, headers, modelo, n, m, nSlack, cols, isMin, pasos, stepNum, slackCol, artCol);
    }

    // ─────────────────────── construcción del tableau ───────────────────────

    private double[][] buildTableau(ModeloLP modelo, int n, int m, int cols,
                                     int[] slackCol, int[] artCol) {
        double[][] t = new double[m + 1][cols];

        for (int i = 0; i < m; i++) {
            Restriccion r = modelo.restricciones().get(i);
            for (int j = 0; j < n; j++) t[i][j] = r.coeficientes().get(j);
            if (slackCol[i] >= 0) {
                t[i][slackCol[i]] = (r.tipo() == TipoRestriccion.GEQ) ? -1.0 : 1.0;
            }
            if (artCol[i] >= 0) t[i][artCol[i]] = 1.0;
            t[i][cols - 1] = r.rhs();
        }
        // La fila z se inicializa en resolver() para Fase 1 y Fase 2 por separado
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

    /** Igual que findEnterCol pero ignora columnas de variables artificiales en Fase 2. */
    private int findEnterColExcluding(double[][] t, int m, int cols, Set<Integer> exclude) {
        int col = -1;
        double minVal = -EPSILON;
        for (int j = 0; j < cols - 1; j++) {
            if (!exclude.contains(j) && t[m][j] < minVal) { minVal = t[m][j]; col = j; }
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
                                                 ModeloLP modelo, int n, int m, int nSlack, int cols,
                                                 boolean isMin, List<SolveStep> pasos, int stepNum,
                                                 int[] slackCol, int[] artCol) {
        double[] solArr = new double[headers.size()];
        for (int i = 0; i < m; i++) solArr[base[i]] = t[i][cols - 1];

        Map<String, Double> valores = new LinkedHashMap<>();
        for (int j = 0; j < n; j++) {
            valores.put(modelo.variables().get(j), round(solArr[j]));
        }

        Map<String, Double> holguras       = SensibilidadCalculator.calcularHolguras(solArr, m, slackCol);
        Map<String, Double> preciosSombra  = SensibilidadCalculator.calcularPreciosSombra(t, base, modelo, n, m, slackCol, artCol);
        RangosSensibilidad  rangos         = SensibilidadCalculator.calcularRangos(t, base, modelo, n, m, cols, n + nSlack, slackCol, artCol, isMin);

        double zOpt = t[m][cols - 1];
        if (isMin) zOpt = -zOpt;

        SolveStatus status = hasMultipleOptima(t, base, m, n, nSlack)
                ? SolveStatus.MULTIPLE_OPTIMO
                : SolveStatus.OPTIMO;

        double zFinal = round(zOpt);
        pasos.add(buildStep(stepNum,
                "Solución óptima encontrada (Dos Fases)",
                "No quedan coeficientes negativos en la fila z. Valor óptimo Z* = " + zFinal + ".",
                t, base, headers,
                Map.of("valorOptimo", zFinal, "status", status.name())));

        return new SolveResult<>(status, new SolucionLP(valores, holguras, zFinal, preciosSombra, rangos), pasos);
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
                        ". Dos Fases requiere b >= 0.");
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
