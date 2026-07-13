package jpap.dev.io_api.domain.dinamica.reemplazo;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.dinamica.DatosEdadEquipo;
import jpap.dev.io_api.domain.dinamica.DecisionOptima;
import jpap.dev.io_api.domain.dinamica.DinamicaUtils;
import jpap.dev.io_api.domain.dinamica.DinamicaValidador;
import jpap.dev.io_api.domain.dinamica.EvaluacionDecision;
import jpap.dev.io_api.domain.dinamica.FilaEtapa;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import jpap.dev.io_api.domain.dinamica.TablaEtapa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reemplazo de equipos a lo largo de un horizonte de planeación, por programación dinámica.
 *
 * Etapa t   → el año t del horizonte.
 * Estado e  → la edad del equipo al inicio del año t (0 = recién comprado).
 * Decisión  → CONSERVAR el equipo un año más, o REEMPLAZARLO por uno nuevo.
 *
 * Recurrencia hacia atrás, con n años de horizonte, ingreso r(e), costo de operación c(e),
 * valor de rescate s(e) y precio de un equipo nuevo I:
 *   f_t(e) = max{ CONSERVAR:   r(e) - c(e) + f_(t+1)(e + 1)
 *                 REEMPLAZAR:  s(e) - I + r(0) - c(0) + f_(t+1)(1) }
 *   f_(n+1)(e) = s(e)     [al cerrar el horizonte se vende el equipo]
 *
 * Cuando el equipo alcanza la edad máxima CONSERVAR deja de ser admisible. Nunca es infactible:
 * REEMPLAZAR siempre está disponible.
 */
public class ReemplazoEquiposSolver {

    private static final MetodoDinamico METODO = MetodoDinamico.REEMPLAZO_EQUIPOS;
    private static final String CONSERVAR = "CONSERVAR";
    private static final String REEMPLAZAR = "REEMPLAZAR";

    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        DinamicaValidador.validarReemplazoEquipos(modelo);

        int horizonte = modelo.horizonteAnios();
        int edadMaxima = modelo.edadMaxima();
        int edadInicial = modelo.edadInicialOrDefault();
        double precioNuevo = modelo.costoCompra();

        Map<Integer, DatosEdadEquipo> tabla = new HashMap<>();
        for (DatosEdadEquipo fila : modelo.tablaEdades()) tabla.put(fila.edad(), fila);

        List<TreeSet<Integer>> alcanzables = edadesAlcanzables(horizonte, edadMaxima, edadInicial);

        List<Map<Integer, Double>> f = new ArrayList<>();
        List<Map<Integer, String>> decision = new ArrayList<>();
        for (int t = 0; t <= horizonte + 1; t++) {
            f.add(new HashMap<>());
            decision.add(new HashMap<>());
        }
        for (int e : alcanzables.get(horizonte)) {
            f.get(horizonte + 1).put(e, tabla.get(e).valorRescate());
        }

        List<TablaEtapa> tablas = new ArrayList<>();
        for (int t = horizonte; t >= 1; t--) {
            List<FilaEtapa> filas = new ArrayList<>();

            for (int e : alcanzables.get(t - 1)) {
                List<EvaluacionDecision> evaluaciones = new ArrayList<>();
                double mejorValor = Double.NEGATIVE_INFINITY;
                String mejorDecision = null;

                if (e + 1 <= edadMaxima) {
                    DatosEdadEquipo actual = tabla.get(e);
                    double contribucion = actual.ingreso() - actual.costoOperacion();
                    double futuro = f.get(t + 1).get(e + 1);
                    double total = contribucion + futuro;
                    evaluaciones.add(new EvaluacionDecision(CONSERVAR, DinamicaUtils.round(contribucion),
                            DinamicaUtils.round(futuro), DinamicaUtils.round(total), false));
                    mejorValor = total;
                    mejorDecision = CONSERVAR;
                }

                double contribucionReemplazo = contribucionReemplazo(tabla, e, precioNuevo);
                double futuroReemplazo = f.get(t + 1).get(1);
                double totalReemplazo = contribucionReemplazo + futuroReemplazo;
                evaluaciones.add(new EvaluacionDecision(REEMPLAZAR, DinamicaUtils.round(contribucionReemplazo),
                        DinamicaUtils.round(futuroReemplazo), DinamicaUtils.round(totalReemplazo), false));
                if (mejorDecision == null || totalReemplazo > mejorValor + 1e-9) {
                    mejorValor = totalReemplazo;
                    mejorDecision = REEMPLAZAR;
                }

                f.get(t).put(e, mejorValor);
                decision.get(t).put(e, mejorDecision);
                filas.add(new FilaEtapa("edad = " + e, marcarOptima(evaluaciones, mejorDecision),
                        mejorDecision, DinamicaUtils.round(mejorValor)));
            }

            tablas.add(new TablaEtapa(t, "Etapa " + t + " — año " + t,
                    recurrenciaDeEtapa(t, horizonte), filas));
        }

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(pasoFormulacion(horizonte, edadMaxima, edadInicial, precioNuevo));
        int numero = 2;
        for (TablaEtapa t : tablas) {
            pasos.add(new SolveStep(numero++, t.nombreEtapa(),
                    String.format("Se evalúa %s para cada edad alcanzable: se compara el ingreso neto de "
                            + "conservar un año más contra el de vender el equipo, comprar uno nuevo y operarlo.",
                            t.recurrencia()),
                    DinamicaUtils.datosTabla(METODO, t)));
        }

        // Recuperación de la política hacia adelante.
        List<DecisionOptima> politica = new ArrayList<>();
        int edad = edadInicial;
        for (int t = 1; t <= horizonte; t++) {
            String d = decision.get(t).get(edad);
            double contribucion = CONSERVAR.equals(d)
                    ? tabla.get(edad).ingreso() - tabla.get(edad).costoOperacion()
                    : contribucionReemplazo(tabla, edad, precioNuevo);
            int siguiente = CONSERVAR.equals(d) ? edad + 1 : 1;
            politica.add(new DecisionOptima(t, "Año " + t, "edad = " + edad, d,
                    DinamicaUtils.round(contribucion), "edad = " + siguiente));
            edad = siguiente;
        }

        double valorOptimo = f.get(1).get(edadInicial);
        pasos.add(new SolveStep(numero, "Recuperación de la política óptima",
                String.format("Partiendo de un equipo de %d año(s) se lee la decisión óptima de cada año. "
                        + "El plan es: %s. El ingreso neto máximo del horizonte es %s (incluye la venta del "
                        + "equipo al cerrar el año %d).",
                        edadInicial, resumenPlan(politica), DinamicaUtils.fmt(valorOptimo), horizonte),
                DinamicaUtils.datosPolitica(METODO, politica, valorOptimo, null)));

        SolucionDinamica solucion = SolucionDinamica.builder()
                .valorOptimo(DinamicaUtils.round(valorOptimo))
                .tablas(tablas)
                .politicaOptima(politica)
                .definicionEtapas("Etapa t = el año t del horizonte (t = 1.." + horizonte + ").")
                .definicionEstados("Estado e = edad del equipo al inicio del año t (0 = nuevo, máximo "
                        + edadMaxima + ").")
                .definicionDecisiones("Decisión: CONSERVAR el equipo un año más, o REEMPLAZARLO (venderlo por su "
                        + "valor de rescate y comprar uno nuevo). A la edad máxima solo cabe REEMPLAZAR.")
                .funcionRecurrencia(recurrenciaGeneral(horizonte, precioNuevo))
                .principioOptimalidad(DinamicaUtils.PRINCIPIO_OPTIMALIDAD)
                .interpretacionPolitica(String.format(
                        "Política óptima: %s. Rinde un ingreso neto de %s a lo largo de %d año(s). La decisión de "
                        + "reemplazar no depende solo del costo de operación del año: pesa también el valor de "
                        + "rescate que se recupera hoy contra los ingresos netos que el equipo aún puede dar.",
                        resumenPlan(politica), DinamicaUtils.fmt(valorOptimo), horizonte))
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    /** Ingreso neto del año cuando se reemplaza: se vende el viejo, se compra uno nuevo y se opera nuevo. */
    private double contribucionReemplazo(Map<Integer, DatosEdadEquipo> tabla, int edad, double precioNuevo) {
        DatosEdadEquipo actual = tabla.get(edad);
        DatosEdadEquipo nuevo = tabla.get(0);
        return actual.valorRescate() - precioNuevo + nuevo.ingreso() - nuevo.costoOperacion();
    }

    /** Edades posibles al inicio de cada año, propagando desde la edad inicial. */
    private List<TreeSet<Integer>> edadesAlcanzables(int horizonte, int edadMaxima, int edadInicial) {
        List<TreeSet<Integer>> alcanzables = new ArrayList<>();
        for (int t = 0; t <= horizonte; t++) alcanzables.add(new TreeSet<>());
        alcanzables.get(0).add(edadInicial);
        for (int t = 1; t <= horizonte; t++) {
            for (int e : alcanzables.get(t - 1)) {
                if (e + 1 <= edadMaxima) alcanzables.get(t).add(e + 1);
                alcanzables.get(t).add(1);
            }
        }
        return alcanzables;
    }

    private SolveStep pasoFormulacion(int horizonte, int edadMaxima, int edadInicial, double precioNuevo) {
        return new SolveStep(1, "Formulación del modelo",
                String.format("Horizonte de %d año(s), equipo de %d año(s) al inicio, edad máxima %d y precio de "
                        + "un equipo nuevo %s. Cada etapa es un año, el estado es la edad del equipo y la decisión "
                        + "es conservarlo o reemplazarlo. Al cerrar el horizonte el equipo se vende por su valor "
                        + "de rescate: esa es la condición de frontera. %s",
                        horizonte, edadInicial, edadMaxima, DinamicaUtils.fmt(precioNuevo),
                        DinamicaUtils.PRINCIPIO_OPTIMALIDAD),
                DinamicaUtils.datosFormulacion(METODO,
                        "Etapa t = año t (t = 1.." + horizonte + ")",
                        "Estado e = edad del equipo al inicio del año t (0.." + edadMaxima + ")",
                        "Decisión: CONSERVAR o REEMPLAZAR",
                        recurrenciaGeneral(horizonte, precioNuevo)));
    }

    private List<EvaluacionDecision> marcarOptima(List<EvaluacionDecision> evaluaciones, String mejorDecision) {
        List<EvaluacionDecision> marcadas = new ArrayList<>(evaluaciones.size());
        for (EvaluacionDecision e : evaluaciones) {
            marcadas.add(new EvaluacionDecision(e.decision(), e.contribucion(), e.valorFuturo(),
                    e.valorTotal(), e.decision().equals(mejorDecision)));
        }
        return marcadas;
    }

    private String recurrenciaGeneral(int horizonte, double precioNuevo) {
        return String.format("f_t(e) = max{ CONSERVAR: r(e) - c(e) + f_(t+1)(e + 1) ; "
                + "REEMPLAZAR: s(e) - %s + r(0) - c(0) + f_(t+1)(1) },  con f_(%d)(e) = s(e)",
                DinamicaUtils.fmt(precioNuevo), horizonte + 1);
    }

    private String recurrenciaDeEtapa(int t, int horizonte) {
        if (t == horizonte) {
            return String.format("f_%d(e) = max{ CONSERVAR: r(e) - c(e) + s(e + 1) ; "
                    + "REEMPLAZAR: s(e) - I + r(0) - c(0) + s(1) }   [último año: el futuro es el rescate]", t);
        }
        return String.format("f_%d(e) = max{ CONSERVAR: r(e) - c(e) + f_%d(e + 1) ; "
                + "REEMPLAZAR: s(e) - I + r(0) - c(0) + f_%d(1) }", t, t + 1, t + 1);
    }

    private String resumenPlan(List<DecisionOptima> politica) {
        StringBuilder sb = new StringBuilder();
        for (DecisionOptima d : politica) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(d.nombreEtapa()).append(" (").append(d.estadoEntrada()).append("): ")
              .append(d.decision().toLowerCase());
        }
        return sb.toString();
    }
}
