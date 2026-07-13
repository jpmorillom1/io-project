package jpap.dev.io_api.domain.lp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SensibilidadCalculator {

    private static final double EPSILON = 1e-9;

    // solArr: vector de solucion basica; slackCol[i] = columna de holgura para restriccion i, -1 si EQ
    public static Map<String, Double> calcularHolguras(double[] solArr, int m, int[] slackCol) {
        Map<String, Double> holguras = new LinkedHashMap<>();
        int sIdx = 0;
        for (int i = 0; i < m; i++) {
            if (slackCol[i] >= 0) {
                holguras.put("s" + (++sIdx), round(Math.abs(solArr[slackCol[i]])));
            }
        }
        return holguras;
    }

    // y_i = c_B^T * B^{-1} * e_i usando coeficientes del objetivo original (sin penalidades M)
    public static Map<String, Double> calcularPreciosSombra(
            double[][] t, int[] base, ModeloLP modelo,
            int n, int m, int[] slackCol, int[] artCol) {

        Map<String, Double> precios = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) {
            TipoRestriccion tipo = modelo.restricciones().get(i).tipo();
            int bInvCol;
            if (tipo == TipoRestriccion.LEQ && slackCol[i] >= 0) {
                bInvCol = slackCol[i];
            } else if (artCol != null && artCol[i] >= 0) {
                bInvCol = artCol[i];
            } else {
                precios.put("R" + (i + 1), 0.0);
                continue;
            }

            double yi = 0.0;
            for (int k = 0; k < m; k++) {
                int bk = base[k];
                double cBk = (bk < n) ? modelo.objetivo().coeficientes().get(bk) : 0.0;
                yi += cBk * t[k][bInvCol];
            }
            precios.put("R" + (i + 1), round(yi));
        }
        return precios;
    }

    // nDecisionAndSlack = n + nSlack (excluye artificiales del ranging)
    public static RangosSensibilidad calcularRangos(
            double[][] t, int[] base, ModeloLP modelo,
            int n, int m, int cols, int nDecisionAndSlack,
            int[] slackCol, int[] artCol, boolean isMin) {

        Set<Integer> basicSet = new HashSet<>();
        Map<Integer, Integer> basicRow = new HashMap<>();
        for (int i = 0; i < m; i++) {
            basicSet.add(base[i]);
            basicRow.put(base[i], i);
        }

        List<RangoCoeficiente> objRanges = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double ck = modelo.objetivo().coeficientes().get(k);
            Double cMin = null, cMax = null;

            if (!basicSet.contains(k)) {
                double rc = t[m][k];
                if (!isMin) cMax = round(ck + rc);
                else        cMin = round(ck - rc);
            } else {
                int r = basicRow.get(k);
                double deltaMin = Double.NEGATIVE_INFINITY;
                double deltaMax = Double.POSITIVE_INFINITY;

                if (!isMin) {
                    for (int j = 0; j < nDecisionAndSlack; j++) {
                        if (basicSet.contains(j)) continue;
                        double trj = t[r][j], rcj = t[m][j];
                        if (trj > EPSILON)       deltaMax = Math.min(deltaMax, rcj / trj);
                        else if (trj < -EPSILON) deltaMin = Math.max(deltaMin, rcj / trj);
                    }
                } else {
                    for (int j = 0; j < nDecisionAndSlack; j++) {
                        if (basicSet.contains(j)) continue;
                        double trj = t[r][j], rcj = t[m][j];
                        if (trj < -EPSILON)      deltaMax = Math.min(deltaMax, -rcj / trj);
                        else if (trj > EPSILON)  deltaMin = Math.max(deltaMin, -rcj / trj);
                    }
                }

                cMin = Double.isInfinite(deltaMin) ? null : round(ck + deltaMin);
                cMax = Double.isInfinite(deltaMax) ? null : round(ck + deltaMax);
            }
            objRanges.add(new RangoCoeficiente(modelo.variables().get(k), round(ck), cMin, cMax));
        }

        List<RangoRHS> rhsRanges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double bi = modelo.restricciones().get(i).rhs();
            TipoRestriccion tipo = modelo.restricciones().get(i).tipo();

            int bInvCol;
            if (tipo == TipoRestriccion.LEQ && slackCol[i] >= 0) {
                bInvCol = slackCol[i];
            } else if (artCol != null && artCol[i] >= 0) {
                bInvCol = artCol[i];
            } else {
                rhsRanges.add(new RangoRHS("R" + (i + 1), round(bi), null, null));
                continue;
            }

            double deltaMin = Double.NEGATIVE_INFINITY;
            double deltaMax = Double.POSITIVE_INFINITY;
            for (int k = 0; k < m; k++) {
                double bInvKI = t[k][bInvCol];
                double bBarK  = t[k][cols - 1];
                if (bInvKI > EPSILON)       deltaMin = Math.max(deltaMin, -bBarK / bInvKI);
                else if (bInvKI < -EPSILON) deltaMax = Math.min(deltaMax, -bBarK / bInvKI);
            }

            Double bMin = Double.isInfinite(deltaMin) ? null : round(bi + deltaMin);
            Double bMax = Double.isInfinite(deltaMax) ? null : round(bi + deltaMax);
            rhsRanges.add(new RangoRHS("R" + (i + 1), round(bi), bMin, bMax));
        }

        return new RangosSensibilidad(objRanges, rhsRanges);
    }

    private static double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
