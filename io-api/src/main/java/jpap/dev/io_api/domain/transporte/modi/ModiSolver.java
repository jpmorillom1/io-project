package jpap.dev.io_api.domain.transporte.modi;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.transporte.Balanceador;
import jpap.dev.io_api.domain.transporte.Balanceador.Balanceado;
import jpap.dev.io_api.domain.transporte.CostoPorMetodo;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import jpap.dev.io_api.domain.transporte.TransporteUtils;
import jpap.dev.io_api.domain.transporte.costominimo.CostoMinimoSolver;
import jpap.dev.io_api.domain.transporte.esquinanoroeste.EsquinaNoroesteSolver;
import jpap.dev.io_api.domain.transporte.vogel.VogelSolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Método MODI (Distribución Modificada / u-v) para optimizar un problema de transporte.
 *
 * Estrategia (decisión del proyecto: "MODI valida cuál es mejor"):
 *   1. Construye la solución básica inicial con los TRES métodos (Esquina Noroeste,
 *      Costo Mínimo, Vogel), reporta el costo de cada uno y ARRANCA desde el más barato.
 *   2. Calcula multiplicadores u/v y costos reducidos de las celdas no básicas.
 *   3. Si algún costo reducido es negativo, mejora trazando el ciclo stepping-stone y
 *      reasignando; repite hasta que todos los costos reducidos sean ≥ 0 (óptimo).
 *   4. Maneja degeneración rellenando con celdas básicas de valor 0 (épsilon) hasta
 *      completar m+n−1 celdas básicas independientes (árbol de expansión).
 *
 * Java puro, sin dependencias de framework.
 */
public class ModiSolver {

    private static final double EPS = 1e-9;
    private static final int MAX_ITER = 200;

    public SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo) {
        Balanceado b = Balanceador.balancear(modelo);
        int fil = b.oferta().length;
        int col = b.demanda().length;

        // 1) Comparar los tres métodos iniciales y arrancar del más barato
        double[][] xNW = EsquinaNoroesteSolver.construir(b);
        double[][] xCM = CostoMinimoSolver.construir(b);
        double[][] xVG = VogelSolver.construir(b);
        double costoNW = TransporteUtils.costoTotal(b.costos(), xNW);
        double costoCM = TransporteUtils.costoTotal(b.costos(), xCM);
        double costoVG = TransporteUtils.costoTotal(b.costos(), xVG);

        List<CostoPorMetodo> comparativa = List.of(
                new CostoPorMetodo(MetodoTransporte.ESQUINA_NOROESTE, costoNW),
                new CostoPorMetodo(MetodoTransporte.COSTO_MINIMO, costoCM),
                new CostoPorMetodo(MetodoTransporte.VOGEL, costoVG));

        MetodoTransporte inicial;
        double[][] x;
        if (costoCM <= costoNW && costoCM <= costoVG) { inicial = MetodoTransporte.COSTO_MINIMO; x = copiar(xCM); }
        else if (costoVG <= costoNW && costoVG <= costoCM) { inicial = MetodoTransporte.VOGEL; x = copiar(xVG); }
        else { inicial = MetodoTransporte.ESQUINA_NOROESTE; x = copiar(xNW); }

        boolean[][] basica = new boolean[fil][col];
        for (int i = 0; i < fil; i++)
            for (int j = 0; j < col; j++)
                basica[i][j] = x[i][j] > EPS;

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(pasoInicialModi(b, x, basica, comparativa, inicial));

        // 2) Asegurar base no degenerada (m+n−1 celdas independientes)
        repararDegeneracion(b, basica, x, pasos);

        SolveStatus status = SolveStatus.OPTIMO;

        // 3) Iterar MODI
        for (int iter = 1; iter <= MAX_ITER; iter++) {
            double[] u = new double[fil];
            double[] v = new double[col];
            calcularPotenciales(b.costos(), basica, u, v, fil, col);

            // Celda no básica con costo reducido más negativo
            int entI = -1, entJ = -1;
            double minReducido = -EPS;
            boolean hayReducidoCero = false;
            double[][] reducidos = new double[fil][col];
            for (int i = 0; i < fil; i++) {
                for (int j = 0; j < col; j++) {
                    if (basica[i][j]) { reducidos[i][j] = 0; continue; }
                    double d = b.costos()[i][j] - u[i] - v[j];
                    reducidos[i][j] = d;
                    if (d < minReducido) { minReducido = d; entI = i; entJ = j; }
                    if (Math.abs(d) < EPS) hayReducidoCero = true;
                }
            }

            if (entI < 0) {
                // Óptimo: ningún costo reducido negativo
                if (hayReducidoCero) status = SolveStatus.MULTIPLE_OPTIMO;
                pasos.add(pasoOptimo(pasos.size(), b, x, basica, u, v, reducidos, status));
                break;
            }

            // 4) Trazar el ciclo y reasignar
            List<int[]> ciclo = CicloSteppingStone.encontrar(new int[]{entI, entJ}, celdasBasicas(basica, fil, col));
            if (ciclo == null) {
                // No debería ocurrir con base reparada; se corta con lo mejor obtenido
                pasos.add(pasoOptimo(pasos.size(), b, x, basica, u, v, reducidos, status));
                break;
            }

            double theta = Double.MAX_VALUE;
            int salI = -1, salJ = -1;
            for (int k = 1; k < ciclo.size(); k += 2) { // posiciones "−"
                int[] c = ciclo.get(k);
                if (x[c[0]][c[1]] < theta - EPS) { theta = x[c[0]][c[1]]; salI = c[0]; salJ = c[1]; }
            }

            for (int k = 0; k < ciclo.size(); k++) {
                int[] c = ciclo.get(k);
                if (k % 2 == 0) x[c[0]][c[1]] += theta;
                else x[c[0]][c[1]] -= theta;
                if (x[c[0]][c[1]] < EPS) x[c[0]][c[1]] = 0.0;
            }
            basica[entI][entJ] = true;
            basica[salI][salJ] = false; // sale exactamente una celda (las demás en 0 siguen básicas: degeneración)

            pasos.add(pasoIteracion(pasos.size(), b, x, basica, u, v, reducidos,
                    new int[]{entI, entJ}, new int[]{salI, salJ}, ciclo, theta, minReducido, iter));
        }

        return new SolveResult<>(status, TransporteUtils.solucion(b, x, comparativa, inicial), pasos);
    }

    // ─── potenciales u/v ─────────────────────────────────────────────────────

    private void calcularPotenciales(double[][] costos, boolean[][] basica,
                                     double[] u, double[] v, int fil, int col) {
        boolean[] uSet = new boolean[fil];
        boolean[] vSet = new boolean[col];
        uSet[0] = true; // u[0] = 0 por convención

        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int i = 0; i < fil; i++) {
                for (int j = 0; j < col; j++) {
                    if (!basica[i][j]) continue;
                    if (uSet[i] && !vSet[j]) { v[j] = costos[i][j] - u[i]; vSet[j] = true; cambio = true; }
                    else if (!uSet[i] && vSet[j]) { u[i] = costos[i][j] - v[j]; uSet[i] = true; cambio = true; }
                }
            }
        }
        // Con base reparada todos quedan fijados; si algo quedara suelto (no debería), queda en 0.
    }

    // ─── degeneración ────────────────────────────────────────────────────────

    /** Rellena celdas básicas de valor 0 (épsilon) que conecten el bosque sin formar ciclo. */
    private void repararDegeneracion(Balanceado b, boolean[][] basica, double[][] x, List<SolveStep> pasos) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        int requeridas = fil + col - 1;

        int[] parent = new int[fil + col];
        for (int k = 0; k < parent.length; k++) parent[k] = k;

        int aristas = 0;
        for (int i = 0; i < fil; i++)
            for (int j = 0; j < col; j++)
                if (basica[i][j] && union(parent, i, fil + j)) aristas++;
                // (si una celda básica formara ciclo — no pasa en BFS válidas — no cuenta como arista)

        if (aristas >= requeridas) return;

        boolean agregada = false;
        for (int i = 0; i < fil && aristas < requeridas; i++) {
            for (int j = 0; j < col && aristas < requeridas; j++) {
                if (basica[i][j]) continue;
                if (union(parent, i, fil + j)) {
                    basica[i][j] = true;   // x[i][j] permanece en 0 (celda épsilon)
                    aristas++;
                    agregada = true;
                }
            }
        }

        if (agregada && pasos != null) {
            Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
            datos.put("celdasBasicas", TransporteUtils.celdasBasicas(basica));
            pasos.add(new SolveStep(pasos.size(), "Corrección de degeneración",
                    "La solución inicial tenía menos de m+n−1 = " + requeridas + " celdas básicas. "
                            + "Se agregan celdas básicas de valor 0 (épsilon) que conectan la tabla sin formar "
                            + "ciclos, para poder calcular los multiplicadores u/v.",
                    datos));
        }
    }

    private boolean union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra == rb) return false;
        parent[ra] = rb;
        return true;
    }

    private int find(int[] parent, int a) {
        while (parent[a] != a) { parent[a] = parent[parent[a]]; a = parent[a]; }
        return a;
    }

    // ─── construcción de pasos ───────────────────────────────────────────────

    private SolveStep pasoInicialModi(Balanceado b, double[][] x, boolean[][] basica,
                                      List<CostoPorMetodo> comparativa, MetodoTransporte inicial) {
        Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
        List<Map<String, Object>> comp = new ArrayList<>();
        for (CostoPorMetodo c : comparativa) {
            comp.add(Map.of("metodo", c.metodo().name(), "costoInicial", c.costoInicial()));
        }
        datos.put("comparativaInicial", comp);
        datos.put("metodoInicial", inicial.name());
        datos.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        return new SolveStep(0, "Solución inicial elegida: " + nombre(inicial),
                "Se compararon los tres métodos iniciales y se arranca desde el de menor costo ("
                        + nombre(inicial) + "). " + TransporteUtils.descripcionBalanceo(b),
                datos);
    }

    private SolveStep pasoIteracion(int numero, Balanceado b, double[][] x, boolean[][] basica,
                                    double[] u, double[] v, double[][] reducidos,
                                    int[] entra, int[] sale, List<int[]> ciclo,
                                    double theta, double reducidoEntra, int iter) {
        Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
        datos.put("u", TransporteUtils.arr(u));
        datos.put("v", TransporteUtils.arr(v));
        datos.put("costosReducidos", reducidosToList(reducidos, basica));
        datos.put("celdaEntrante", entra);
        datos.put("celdaSaliente", sale);
        datos.put("ciclo", new ArrayList<>(ciclo));
        datos.put("theta", TransporteUtils.round(theta));
        datos.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        return new SolveStep(numero,
                "Iteración " + iter + ": entra (" + b.origenes().get(entra[0]) + " → " + b.destinos().get(entra[1])
                        + "), sale (" + b.origenes().get(sale[0]) + " → " + b.destinos().get(sale[1]) + ")",
                "El costo reducido más negativo es " + TransporteUtils.round(reducidoEntra)
                        + " en la celda entrante. Se traza su ciclo y se reasigna θ = " + TransporteUtils.round(theta)
                        + " unidades. Nuevo costo total = " + TransporteUtils.costoTotal(b.costos(), x) + ".",
                datos);
    }

    private SolveStep pasoOptimo(int numero, Balanceado b, double[][] x, boolean[][] basica,
                                 double[] u, double[] v, double[][] reducidos, SolveStatus status) {
        Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
        datos.put("u", TransporteUtils.arr(u));
        datos.put("v", TransporteUtils.arr(v));
        datos.put("costosReducidos", reducidosToList(reducidos, basica));
        datos.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        datos.put("status", status.name());
        String desc = "Todos los costos reducidos de las celdas no básicas son ≥ 0: la solución es óptima. "
                + "Costo total mínimo = " + TransporteUtils.costoTotal(b.costos(), x) + ".";
        if (status == SolveStatus.MULTIPLE_OPTIMO)
            desc += " Existe al menos una celda no básica con costo reducido 0 → hay óptimos múltiples.";
        return new SolveStep(numero, "Solución óptima encontrada", desc, datos);
    }

    // ─── utilidades ──────────────────────────────────────────────────────────

    private List<List<Double>> reducidosToList(double[][] reducidos, boolean[][] basica) {
        List<List<Double>> out = new ArrayList<>(reducidos.length);
        for (int i = 0; i < reducidos.length; i++) {
            List<Double> fila = new ArrayList<>(reducidos[i].length);
            for (int j = 0; j < reducidos[i].length; j++)
                fila.add(basica[i][j] ? null : TransporteUtils.round(reducidos[i][j]));
            out.add(fila);
        }
        return out;
    }

    private List<int[]> celdasBasicas(boolean[][] basica, int fil, int col) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < fil; i++)
            for (int j = 0; j < col; j++)
                if (basica[i][j]) out.add(new int[]{i, j});
        return out;
    }

    private double[][] copiar(double[][] m) {
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    private String nombre(MetodoTransporte m) {
        return switch (m) {
            case ESQUINA_NOROESTE -> "Esquina Noroeste";
            case COSTO_MINIMO -> "Costo Mínimo";
            case VOGEL -> "Vogel (VAM)";
            case MODI -> "MODI";
        };
    }
}
