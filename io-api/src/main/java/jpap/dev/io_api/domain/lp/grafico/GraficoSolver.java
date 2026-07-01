package jpap.dev.io_api.domain.lp.grafico;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Método gráfico para PL con exactamente 2 variables.
 * Asume no-negatividad implícita (x1 >= 0, x2 >= 0).
 * Soporta LEQ, GEQ y EQ.
 */
public class GraficoSolver {

    private static final double EPSILON = 1e-9;

    public SolveResult<SolucionGrafica> resolver(ModeloLP modelo) {
        validar(modelo);

        String var1 = modelo.variables().get(0);
        String var2 = modelo.variables().get(1);
        boolean isMin = modelo.objetivo().tipo() == TipoObjetivo.MINIMIZAR;
        double c1 = modelo.objetivo().coeficientes().get(0);
        double c2 = modelo.objetivo().coeficientes().get(1);
        int m = modelo.restricciones().size();

        List<SolveStep> pasos = new ArrayList<>();

        // ── 1. Encontrar vértices candidatos (intersecciones de pares de líneas)
        List<double[]> candidatos = generarCandidatos(modelo);

        // ── 2. Filtrar los factibles
        List<double[]> factibles = candidatos.stream()
                .filter(p -> esFeasible(p, modelo))
                .collect(Collectors.toList());
        factibles = deduplicar(factibles);

        // ── 3. Calcular límites del gráfico
        double[] bounds = computeBounds(modelo, factibles);
        double xMax = bounds[0];
        double yMax = bounds[1];

        // ── 4. Datos de líneas para graficar
        List<LineaGraficoData> todasLineas = computeLineasData(modelo, xMax, yMax);

        // ── Paso 0: modelo recibido
        pasos.add(buildStep(0,
                "Modelo de 2 variables recibido",
                "Se procede a graficar las " + m + " restriccione(s) para identificar la región factible.",
                var1, var2, xMax, yMax, List.of(), List.of(), List.of()));

        // ── Pasos 1..m: agregar restricciones progresivamente
        List<LineaGraficoData> lineasAcum = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            lineasAcum.add(todasLineas.get(i));
            Restriccion r = modelo.restricciones().get(i);
            pasos.add(buildStep(i + 1,
                    "Graficando restricción " + (i + 1),
                    "Se traza la línea: " + formatRestriccion(modelo.variables(), r),
                    var1, var2, xMax, yMax,
                    new ArrayList<>(lineasAcum), List.of(), List.of()));
        }

        // ── Caso infactible
        if (factibles.isEmpty()) {
            pasos.add(buildStep(m + 1,
                    "Región infactible",
                    "No existe ningún punto que satisfaga simultáneamente todas las restricciones.",
                    var1, var2, xMax, yMax, todasLineas, List.of(), List.of()));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        // ── Caso no acotado
        if (esNoAcotado(c1, c2, isMin, modelo)) {
            List<PuntoVertice> vertexData = factibles.stream()
                    .map(p -> toVertice(p, c1, c2, false))
                    .collect(Collectors.toList());
            pasos.add(buildStep(m + 1,
                    "Problema no acotado",
                    "La región factible se extiende infinitamente en la dirección de mejora del objetivo.",
                    var1, var2, xMax, yMax, todasLineas, vertexData, List.of()));
            return new SolveResult<>(SolveStatus.NO_ACOTADO, null, pasos);
        }

        // ── Ordenar vértices formando el polígono convexo
        List<double[]> region = ordenarVertices(factibles);

        // ── Paso región factible
        List<PuntoVertice> verticesSinZ = factibles.stream()
                .map(p -> toVertice(p, c1, c2, false))
                .collect(Collectors.toList());
        List<List<Double>> regionData = region.stream()
                .map(p -> List.of(round(p[0]), round(p[1])))
                .collect(Collectors.toList());

        pasos.add(buildStep(m + 1,
                "Región factible identificada",
                "El área sombreada es la región factible. Sus vértices son los candidatos para el óptimo.",
                var1, var2, xMax, yMax, todasLineas, verticesSinZ, regionData));

        // ── Encontrar el óptimo
        double[] optimoTemp = null;
        double zOptTemp = isMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (double[] p : factibles) {
            double z = c1 * p[0] + c2 * p[1];
            if (isMin ? z < zOptTemp - EPSILON : z > zOptTemp + EPSILON) {
                zOptTemp = z;
                optimoTemp = p;
            }
        }

        final double zOpt = zOptTemp;
        final double zOptimoFinal = round(zOpt);
        final double[] optimoFinal = optimoTemp;

        long countOptimos = factibles.stream()
                .filter(p -> Math.abs(c1 * p[0] + c2 * p[1] - zOpt) < EPSILON)
                .count();
        SolveStatus status = countOptimos > 1 ? SolveStatus.MULTIPLE_OPTIMO : SolveStatus.OPTIMO;

        // ── Paso evaluación en vértices
        List<PuntoVertice> verticesConZ = factibles.stream()
                .map(p -> toVertice(p, c1, c2, Math.abs(c1 * p[0] + c2 * p[1] - zOpt) < EPSILON))
                .collect(Collectors.toList());

        String descEval = "Z = " + formatObjetivo(c1, c2, modelo.variables())
                + "  →  óptimo en " + var1 + "=" + round(optimoFinal[0])
                + ", " + var2 + "=" + round(optimoFinal[1])
                + "  →  Z* = " + zOptimoFinal;
        pasos.add(buildStep(m + 2,
                "Evaluación en vértices",
                descEval,
                var1, var2, xMax, yMax, todasLineas, verticesConZ, regionData));

        // ── Paso solución óptima
        pasos.add(buildStep(m + 3,
                "Solución óptima",
                "Z* = " + zOptimoFinal + "  en  " + var1 + " = " + round(optimoFinal[0])
                        + ",  " + var2 + " = " + round(optimoFinal[1]),
                var1, var2, xMax, yMax, todasLineas, verticesConZ, regionData));

        // ── Construir solución
        Map<String, Double> valores = new LinkedHashMap<>();
        valores.put(var1, round(optimoFinal[0]));
        valores.put(var2, round(optimoFinal[1]));

        List<PuntoVertice> puntosFinales = factibles.stream()
                .map(p -> toVertice(p, c1, c2, Math.abs(c1 * p[0] + c2 * p[1] - zOpt) < EPSILON))
                .collect(Collectors.toList());

        SolucionGrafica solucion = new SolucionGrafica(valores, zOptimoFinal, puntosFinales, regionData, xMax, yMax);
        return new SolveResult<>(status, solucion, pasos);
    }

    // ─────────────────── generación de candidatos ───────────────────────────

    private List<double[]> generarCandidatos(ModeloLP modelo) {
        int m = modelo.restricciones().size();
        // Líneas: restricciones explícitas + x1=0 + x2=0
        double[][] lineas = new double[m + 2][3]; // [a, b, c] para ax + by = c

        for (int i = 0; i < m; i++) {
            Restriccion r = modelo.restricciones().get(i);
            lineas[i][0] = r.coeficientes().get(0);
            lineas[i][1] = r.coeficientes().get(1);
            lineas[i][2] = r.rhs();
        }
        lineas[m]     = new double[]{1, 0, 0}; // x1 = 0
        lineas[m + 1] = new double[]{0, 1, 0}; // x2 = 0

        List<double[]> candidatos = new ArrayList<>();
        int total = m + 2;
        for (int i = 0; i < total; i++) {
            for (int j = i + 1; j < total; j++) {
                double[] p = intersectar(lineas[i], lineas[j]);
                if (p != null) candidatos.add(p);
            }
        }
        return candidatos;
    }

    private double[] intersectar(double[] l1, double[] l2) {
        double det = l1[0] * l2[1] - l2[0] * l1[1];
        if (Math.abs(det) < EPSILON) return null;
        double x = (l1[2] * l2[1] - l2[2] * l1[1]) / det;
        double y = (l1[0] * l2[2] - l2[0] * l1[2]) / det;
        return new double[]{x, y};
    }

    // ─────────────────── factibilidad ───────────────────────────────────────

    private boolean esFeasible(double[] p, ModeloLP modelo) {
        if (p[0] < -EPSILON || p[1] < -EPSILON) return false;
        for (Restriccion r : modelo.restricciones()) {
            double lhs = r.coeficientes().get(0) * p[0] + r.coeficientes().get(1) * p[1];
            boolean ok = switch (r.tipo()) {
                case LEQ -> lhs <= r.rhs() + EPSILON;
                case GEQ -> lhs >= r.rhs() - EPSILON;
                case EQ  -> Math.abs(lhs - r.rhs()) <= EPSILON;
            };
            if (!ok) return false;
        }
        return true;
    }

    // ─────────────────── no acotado ─────────────────────────────────────────

    private boolean esNoAcotado(double c1, double c2, boolean isMin, ModeloLP modelo) {
        // Dirección de mejora (proyectada al ortante no-negativo)
        double d1 = isMin ? -c1 : c1;
        double d2 = isMin ? -c2 : c2;
        d1 = Math.max(0, d1);
        d2 = Math.max(0, d2);

        if (d1 < EPSILON && d2 < EPSILON) return false;

        double norm = Math.sqrt(d1 * d1 + d2 * d2);
        double[] test = {1e6 * d1 / norm, 1e6 * d2 / norm};
        return esFeasible(test, modelo);
    }

    // ─────────────────── límites del gráfico ────────────────────────────────

    private double[] computeBounds(ModeloLP modelo, List<double[]> factibles) {
        double xMax = 1.0;
        double yMax = 1.0;

        for (Restriccion r : modelo.restricciones()) {
            double a = r.coeficientes().get(0);
            double b = r.coeficientes().get(1);
            double c = r.rhs();
            if (Math.abs(a) > EPSILON && c / a > 0) xMax = Math.max(xMax, c / a);
            if (Math.abs(b) > EPSILON && c / b > 0) yMax = Math.max(yMax, c / b);
        }
        for (double[] p : factibles) {
            xMax = Math.max(xMax, p[0]);
            yMax = Math.max(yMax, p[1]);
        }

        xMax = Math.ceil(xMax * 1.3);
        yMax = Math.ceil(yMax * 1.3);
        return new double[]{xMax, yMax};
    }

    // ─────────────────── datos de líneas para el gráfico ────────────────────

    private List<LineaGraficoData> computeLineasData(ModeloLP modelo, double xMax, double yMax) {
        List<LineaGraficoData> lineas = new ArrayList<>();
        List<String> vars = modelo.variables();

        for (int i = 0; i < modelo.restricciones().size(); i++) {
            Restriccion r = modelo.restricciones().get(i);
            double a = r.coeficientes().get(0);
            double b = r.coeficientes().get(1);
            double c = r.rhs();

            List<Map<String, Double>> puntos = lineaEnVentana(a, b, c, xMax, yMax);
            String etiqueta = "R" + (i + 1) + ": " + formatRestriccion(vars, r);
            lineas.add(new LineaGraficoData(i, etiqueta, r.tipo().name(), puntos));
        }
        return lineas;
    }

    private List<Map<String, Double>> lineaEnVentana(double a, double b, double c,
                                                       double xMax, double yMax) {
        List<double[]> pts = new ArrayList<>();

        if (Math.abs(b) > EPSILON) {
            addPt(pts, 0.0,  c / b,                    xMax, yMax);
            addPt(pts, xMax, (c - a * xMax) / b,       xMax, yMax);
        }
        if (Math.abs(a) > EPSILON) {
            addPt(pts, c / a,              0.0,  xMax, yMax);
            addPt(pts, (c - b * yMax) / a, yMax, xMax, yMax);
        }

        return pts.stream()
                .limit(2)
                .map(p -> Map.of("x", round(p[0]), "y", round(p[1])))
                .collect(Collectors.toList());
    }

    private void addPt(List<double[]> pts, double x, double y, double xMax, double yMax) {
        if (x < -EPSILON || x > xMax + EPSILON) return;
        if (y < -EPSILON || y > yMax + EPSILON) return;
        double cx = Math.max(0, Math.min(xMax, x));
        double cy = Math.max(0, Math.min(yMax, y));
        // evitar duplicados
        for (double[] p : pts) {
            if (Math.abs(p[0] - cx) < EPSILON && Math.abs(p[1] - cy) < EPSILON) return;
        }
        pts.add(new double[]{cx, cy});
    }

    // ─────────────────── ordenar polígono convexo ───────────────────────────

    private List<double[]> ordenarVertices(List<double[]> vs) {
        if (vs.size() <= 2) return new ArrayList<>(vs);
        double cx = vs.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double cy = vs.stream().mapToDouble(p -> p[1]).average().orElse(0);
        return vs.stream()
                .sorted(Comparator.comparingDouble(p -> Math.atan2(p[1] - cy, p[0] - cx)))
                .collect(Collectors.toList());
    }

    // ─────────────────── deduplicar ─────────────────────────────────────────

    private List<double[]> deduplicar(List<double[]> pts) {
        List<double[]> res = new ArrayList<>();
        for (double[] p : pts) {
            boolean dup = res.stream().anyMatch(
                    q -> Math.abs(q[0] - p[0]) < EPSILON && Math.abs(q[1] - p[1]) < EPSILON);
            if (!dup) res.add(p);
        }
        return res;
    }

    // ─────────────────── construcción de pasos ──────────────────────────────

    private SolveStep buildStep(int num, String titulo, String desc,
                                 String var1, String var2, double xMax, double yMax,
                                 List<LineaGraficoData> lineas,
                                 List<PuntoVertice> vertices,
                                 List<?> region) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "GRAFICO");
        datos.put("var1", var1);
        datos.put("var2", var2);
        datos.put("xMax", xMax);
        datos.put("yMax", yMax);
        datos.put("lineas", lineas);
        datos.put("vertices", vertices);
        datos.put("region", region);
        return new SolveStep(num, titulo, desc, datos);
    }

    // ─────────────────── utilidades ─────────────────────────────────────────

    private PuntoVertice toVertice(double[] p, double c1, double c2, boolean esOptimo) {
        double x = round(p[0]);
        double y = round(p[1]);
        double z = round(c1 * p[0] + c2 * p[1]);
        return new PuntoVertice(x, y, z, esOptimo, "(" + x + ", " + y + ")");
    }

    private String formatRestriccion(List<String> vars, Restriccion r) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < vars.size(); j++) {
            if (j > 0) sb.append(" + ");
            sb.append(r.coeficientes().get(j)).append(vars.get(j));
        }
        sb.append(switch (r.tipo()) {
            case LEQ -> " ≤ ";
            case GEQ -> " ≥ ";
            case EQ  -> " = ";
        });
        sb.append(r.rhs());
        return sb.toString();
    }

    private String formatObjetivo(double c1, double c2, List<String> vars) {
        return c1 + vars.get(0) + " + " + c2 + vars.get(1);
    }

    private void validar(ModeloLP modelo) {
        if (modelo.variables() == null || modelo.variables().size() != 2)
            throw new IllegalArgumentException(
                    "El método gráfico solo admite exactamente 2 variables de decisión.");
        if (modelo.objetivo() == null || modelo.objetivo().coeficientes().size() != 2)
            throw new IllegalArgumentException("La función objetivo debe tener exactamente 2 coeficientes.");
        if (modelo.restricciones() == null || modelo.restricciones().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos una restricción.");
        for (int i = 0; i < modelo.restricciones().size(); i++) {
            Restriccion r = modelo.restricciones().get(i);
            if (r.coeficientes() == null || r.coeficientes().size() != 2)
                throw new IllegalArgumentException(
                        "Restricción " + (i + 1) + " debe tener 2 coeficientes.");
        }
    }

    private double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
