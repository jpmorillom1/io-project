package jpap.dev.io_api.domain.entera.branchandbound;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.SolucionEntera;
import jpap.dev.io_api.domain.entera.TipoVariable;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.domain.lp.granm.GranMSolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Programación Lineal Entera por <strong>Branch &amp; Bound</strong> sobre el Simplex existente.
 *
 * <p>Cada nodo del árbol es una relajación lineal que se resuelve con {@link GranMSolver}
 * (maneja LEQ/GEQ/EQ con b ≥ 0 — necesario porque las ramas x_i ≥ ⌈v⌉ introducen restricciones
 * GEQ). Si la solución de la relajación ya es entera en todas las
 * variables ENTERA/BINARIA, es una candidata (incumbente); si alguna es fraccionaria, se
 * ramifica creando dos hijos con {@code x_i <= floor(v)} y {@code x_i >= ceil(v)}. Las variables
 * BINARIA reciben además la cota implícita {@code x_i <= 1}.</p>
 *
 * <p>Poda: por infactibilidad (la rama no tiene región factible) y por cota (el óptimo de la
 * relajación no puede mejorar el incumbente). Java puro, sin dependencias de framework.
 * Infactible / no acotado son <em>resultados</em> (SolveStatus), nunca excepciones; las
 * excepciones se reservan para entradas malformadas.</p>
 */
public class BranchAndBoundSolver {

    private static final double EPSILON = 1e-9;
    private static final double INT_TOL = 1e-6;
    private static final int MAX_NODOS = 5000;

    /** Nodo del árbol de exploración: acumula las restricciones de ramificación desde la raíz. */
    private record Nodo(int id, int padreId, List<Restriccion> ramas, String descripcionRama) {}

    public SolveResult<SolucionEntera> resolver(ModeloEntero modelo) {
        validar(modelo);

        ModeloLP relajacion = modelo.relajacion();
        List<String> variables = relajacion.variables();
        int n = variables.size();
        boolean isMin = relajacion.objetivo().tipo() == TipoObjetivo.MINIMIZAR;

        // Restricciones base + cota implícita x_i <= 1 de cada variable BINARIA.
        List<Restriccion> base = new ArrayList<>(relajacion.restricciones());
        for (int j = 0; j < n; j++) {
            if (modelo.tiposVariable().get(j) == TipoVariable.BINARIA) {
                base.add(new Restriccion(vectorUnitario(n, j), TipoRestriccion.LEQ, 1.0));
            }
        }

        GranMSolver lpSolver = new GranMSolver();
        List<SolveStep> pasos = new ArrayList<>();

        Map<String, Double> mejorValores = null;
        double mejorZ = isMin ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        double valorRelajacionRaiz = Double.NaN;

        Deque<Nodo> pila = new ArrayDeque<>();
        pila.push(new Nodo(0, -1, List.of(), "Relajación LP inicial (raíz)"));
        int nodosExplorados = 0;
        int siguienteId = 1;

        while (!pila.isEmpty()) {
            if (nodosExplorados >= MAX_NODOS) {
                pasos.add(new SolveStep(pasos.size(),
                        "Límite de nodos alcanzado",
                        "Se exploraron " + nodosExplorados + " nodos (tope de seguridad " + MAX_NODOS +
                                "). Se devuelve el mejor incumbente encontrado hasta ahora.",
                        Map.of("nodosExplorados", nodosExplorados, "limite", MAX_NODOS)));
                break;
            }

            Nodo nodo = pila.pop();
            nodosExplorados++;

            List<Restriccion> restNodo = new ArrayList<>(base);
            restNodo.addAll(nodo.ramas());
            ModeloLP lpNodo = new ModeloLP(variables,
                    new FuncionObjetivo(relajacion.objetivo().coeficientes(), relajacion.objetivo().tipo()),
                    restNodo);

            SolveResult<SolucionLP> res = lpSolver.resolver(lpNodo);
            SolveStatus st = res.status();

            if (nodo.id() == 0 && st == SolveStatus.NO_ACOTADO) {
                pasos.add(pasoNodo(pasos.size(), nodo, st, null, null,
                        "NO_ACOTADO", "La relajación LP de la raíz no está acotada: el modelo entero tampoco lo está."));
                return new SolveResult<>(SolveStatus.NO_ACOTADO, null, pasos);
            }

            if (st == SolveStatus.INFACTIBLE || st == SolveStatus.NO_ACOTADO || res.solution() == null) {
                pasos.add(pasoNodo(pasos.size(), nodo, st, null, null,
                        "PODA_INFACTIBLE", "La relajación de este nodo es infactible: la rama se poda (no puede contener soluciones enteras)."));
                continue;
            }

            SolucionLP sol = res.solution();
            double z = sol.valorOptimo();
            if (nodo.id() == 0) valorRelajacionRaiz = z;

            // Poda por cota: la relajación no puede mejorar el incumbente actual.
            boolean hayIncumbente = mejorValores != null;
            if (hayIncumbente && (isMin ? z >= mejorZ - EPSILON : z <= mejorZ + EPSILON)) {
                pasos.add(pasoNodo(pasos.size(), nodo, st, z, sol.valores(),
                        "PODA_COTA", "z relajado = " + round(z) + " no mejora el incumbente Z* = " + round(mejorZ) +
                                ": la rama se poda por cota."));
                continue;
            }

            // ¿Es entera la solución en todas las variables ENTERA/BINARIA?
            int varFrac = seleccionarVariableFraccionaria(modelo, variables, sol.valores());

            if (varFrac < 0) {
                // Candidata entera: actualiza el incumbente (la poda por cota ya garantizó mejora).
                mejorValores = redondearEnteras(modelo, variables, sol.valores());
                mejorZ = z;
                pasos.add(pasoNodo(pasos.size(), nodo, st, z, mejorValores,
                        "INCUMBENTE", "Solución ENTERA factible con Z = " + round(z) +
                                ". Es el nuevo mejor incumbente."));
                continue;
            }

            // Ramificar sobre la variable fraccionaria elegida.
            String nombre = variables.get(varFrac);
            double valor = sol.valores().get(nombre);
            long piso = (long) Math.floor(valor + EPSILON);
            long techo = piso + 1;

            Restriccion ramaLeq = new Restriccion(vectorUnitario(n, varFrac), TipoRestriccion.LEQ, (double) piso);
            Restriccion ramaGeq = new Restriccion(vectorUnitario(n, varFrac), TipoRestriccion.GEQ, (double) techo);

            pasos.add(pasoNodo(pasos.size(), nodo, st, z, sol.valores(),
                    "RAMIFICA", "La variable '" + nombre + "' = " + round(valor) +
                            " es fraccionaria. Se ramifica en '" + nombre + " <= " + piso +
                            "' y '" + nombre + " >= " + techo + "'.",
                    Map.of("varRamificada", nombre, "valorFraccionario", round(valor),
                            "ramaIzquierda", nombre + " <= " + piso,
                            "ramaDerecha", nombre + " >= " + techo)));

            // LIFO: apilamos primero la rama >= techo para explorar antes la rama <= piso.
            pila.push(new Nodo(siguienteId++, nodo.id(), concatenar(nodo.ramas(), ramaGeq), nombre + " >= " + techo));
            pila.push(new Nodo(siguienteId++, nodo.id(), concatenar(nodo.ramas(), ramaLeq), nombre + " <= " + piso));
        }

        if (mejorValores == null) {
            pasos.add(new SolveStep(pasos.size(),
                    "Sin solución entera factible",
                    "Se agotó el árbol de Branch & Bound sin encontrar ninguna solución que satisfaga " +
                            "las restricciones de integralidad.",
                    Map.of("status", SolveStatus.INFACTIBLE.name(), "nodosExplorados", nodosExplorados)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        double brecha = round(Math.abs(valorRelajacionRaiz - mejorZ));
        pasos.add(new SolveStep(pasos.size(),
                "Solución óptima entera encontrada",
                "Z* = " + round(mejorZ) + " con la asignación entera óptima. La relajación LP de la raíz daba " +
                        round(valorRelajacionRaiz) + " (brecha de integralidad = " + brecha +
                        "): por eso no basta con redondear la relajación.",
                datosSolucionFinal(mejorValores, mejorZ, valorRelajacionRaiz, brecha, nodosExplorados)));

        SolucionEntera solucion = new SolucionEntera(mejorValores, round(mejorZ), round(valorRelajacionRaiz), nodosExplorados);
        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    // ─────────────────────── selección de variable ──────────────────────────

    /**
     * Devuelve el índice de la variable ENTERA/BINARIA más fraccionaria (distancia al entero
     * más cercano máxima; desempate por menor índice), o -1 si todas son enteras.
     */
    private int seleccionarVariableFraccionaria(ModeloEntero modelo, List<String> variables,
                                                Map<String, Double> valores) {
        int elegido = -1;
        double mejorDistancia = INT_TOL;
        for (int j = 0; j < variables.size(); j++) {
            TipoVariable tipo = modelo.tiposVariable().get(j);
            if (tipo == TipoVariable.CONTINUA) continue;
            double v = valores.getOrDefault(variables.get(j), 0.0);
            double distancia = Math.abs(v - Math.rint(v));
            if (distancia > mejorDistancia + EPSILON) {
                mejorDistancia = distancia;
                elegido = j;
            }
        }
        return elegido;
    }

    private Map<String, Double> redondearEnteras(ModeloEntero modelo, List<String> variables,
                                                 Map<String, Double> valores) {
        Map<String, Double> limpio = new LinkedHashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            String nombre = variables.get(j);
            double v = valores.getOrDefault(nombre, 0.0);
            if (modelo.tiposVariable().get(j) != TipoVariable.CONTINUA) v = Math.rint(v);
            limpio.put(nombre, round(v));
        }
        return limpio;
    }

    // ─────────────────────── construcción de pasos ──────────────────────────

    private SolveStep pasoNodo(int num, Nodo nodo, SolveStatus estadoLp, Double z,
                               Map<String, Double> valores, String accion, String descripcion) {
        return pasoNodo(num, nodo, estadoLp, z, valores, accion, descripcion, Map.of());
    }

    private SolveStep pasoNodo(int num, Nodo nodo, SolveStatus estadoLp, Double z,
                               Map<String, Double> valores, String accion, String descripcion,
                               Map<String, Object> extra) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nodoId", nodo.id());
        datos.put("padreId", nodo.padreId());
        datos.put("rama", nodo.descripcionRama());
        datos.put("estadoRelajacion", estadoLp != null ? estadoLp.name() : "SIN_SOLUCION");
        if (z != null) datos.put("zRelajacion", round(z));
        if (valores != null) datos.put("valoresRelajacion", redondearValores(valores));
        datos.put("accion", accion);
        datos.putAll(extra);

        String titulo = "Nodo " + nodo.id() + " — " + nodo.descripcionRama();
        return new SolveStep(num, titulo, descripcion, datos);
    }

    private Map<String, Object> datosSolucionFinal(Map<String, Double> valores, double z,
                                                   double zRelajacion, double brecha, int nodos) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("valores", valores);
        datos.put("valorOptimo", round(z));
        datos.put("valorRelajacion", round(zRelajacion));
        datos.put("brechaIntegralidad", brecha);
        datos.put("nodosExplorados", nodos);
        datos.put("status", SolveStatus.OPTIMO.name());
        return datos;
    }

    private Map<String, Double> redondearValores(Map<String, Double> valores) {
        Map<String, Double> copia = new LinkedHashMap<>();
        valores.forEach((k, v) -> copia.put(k, round(v)));
        return copia;
    }

    // ─────────────────────── utilidades ─────────────────────────────────────

    private List<Double> vectorUnitario(int n, int idx) {
        List<Double> v = new ArrayList<>(n);
        for (int j = 0; j < n; j++) v.add(j == idx ? 1.0 : 0.0);
        return v;
    }

    private List<Restriccion> concatenar(List<Restriccion> ramas, Restriccion nueva) {
        List<Restriccion> lista = new ArrayList<>(ramas);
        lista.add(nueva);
        return lista;
    }

    private void validar(ModeloEntero modelo) {
        if (modelo == null || modelo.relajacion() == null)
            throw new IllegalArgumentException("El modelo entero necesita una relajación LP.");
        ModeloLP r = modelo.relajacion();
        if (r.variables() == null || r.variables().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos una variable.");
        if (modelo.tiposVariable() == null || modelo.tiposVariable().size() != r.variables().size())
            throw new IllegalArgumentException(
                    "tiposVariable debe tener un tipo por cada variable (" + r.variables().size() + ").");
        // La validación fina de objetivo/restricciones la realiza GranMSolver al resolver la raíz.
    }

    private double round(double v) {
        if (Math.abs(v) < EPSILON) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
