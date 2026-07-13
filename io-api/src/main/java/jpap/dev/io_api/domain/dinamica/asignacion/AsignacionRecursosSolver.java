package jpap.dev.io_api.domain.dinamica.asignacion;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.dinamica.ActividadRecurso;
import jpap.dev.io_api.domain.dinamica.DecisionOptima;
import jpap.dev.io_api.domain.dinamica.DinamicaUtils;
import jpap.dev.io_api.domain.dinamica.DinamicaValidador;
import jpap.dev.io_api.domain.dinamica.EvaluacionDecision;
import jpap.dev.io_api.domain.dinamica.FilaEtapa;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SentidoOptimizacion;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import jpap.dev.io_api.domain.dinamica.TablaEtapa;

import java.util.ArrayList;
import java.util.List;

/**
 * Asignación de un recurso discreto entre varias actividades (o periodos, o rubros de un
 * presupuesto), por programación dinámica determinística.
 *
 * Etapa i   → actividad i.
 * Estado s  → unidades de recurso todavía disponibles al llegar a la etapa i.
 * Decisión x→ cuántas unidades asignarle a la actividad i, con 0 &lt;= x &lt;= s.
 *
 * Recurrencia hacia atrás, con n actividades:
 *   f_i(s) = opt_{0 &lt;= x &lt;= s} [ r_i(x) + f_(i+1)(s - x) ],   f_(n+1)(s) = 0
 *
 * Nunca es infactible: x = 0 siempre es una decisión admisible. Se permite dejar recurso
 * sin asignar (el óptimo lo hará solo si eso es lo mejor).
 */
public class AsignacionRecursosSolver {

    private static final MetodoDinamico METODO = MetodoDinamico.ASIGNACION_RECURSOS;

    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        DinamicaValidador.validarAsignacionRecursos(modelo);

        SentidoOptimizacion sentido = modelo.sentidoOrDefault();
        List<ActividadRecurso> actividades = modelo.actividades();
        int n = actividades.size();
        int recurso = modelo.recursoTotal();

        // f[i][s] con i = 1..n+1; decision[i][s] guarda la x que alcanza el óptimo.
        double[][] f = new double[n + 2][recurso + 1];
        int[][] decision = new int[n + 2][recurso + 1];
        List<TablaEtapa> tablas = new ArrayList<>();

        for (int i = n; i >= 1; i--) {
            ActividadRecurso actividad = actividades.get(i - 1);
            List<FilaEtapa> filas = new ArrayList<>();

            for (int s = 0; s <= recurso; s++) {
                double mejorValor = DinamicaUtils.peorValor(sentido);
                int mejorX = 0;
                List<EvaluacionDecision> evaluaciones = new ArrayList<>();

                for (int x = 0; x <= s; x++) {
                    double contribucion = actividad.retornos().get(x);
                    double futuro = f[i + 1][s - x];
                    double total = contribucion + futuro;
                    evaluaciones.add(new EvaluacionDecision("x = " + x,
                            DinamicaUtils.round(contribucion), DinamicaUtils.round(futuro),
                            DinamicaUtils.round(total), false));
                    if (x == 0 || DinamicaUtils.esMejor(sentido, total, mejorValor)) {
                        mejorValor = total;
                        mejorX = x;
                    }
                }

                f[i][s] = mejorValor;
                decision[i][s] = mejorX;
                filas.add(new FilaEtapa("s = " + s, marcarOptima(evaluaciones, mejorX),
                        "x = " + mejorX, DinamicaUtils.round(mejorValor)));
            }

            tablas.add(new TablaEtapa(i, "Etapa " + i + " — " + actividad.nombre(),
                    recurrenciaDeEtapa(i, n, sentido, actividad.nombre()), filas));
        }

        // Recuperación de la política hacia adelante desde el estado inicial s = recursoTotal.
        List<DecisionOptima> politica = new ArrayList<>();
        int s = recurso;
        for (int i = 1; i <= n; i++) {
            ActividadRecurso actividad = actividades.get(i - 1);
            int x = decision[i][s];
            double contribucion = actividad.retornos().get(x);
            politica.add(new DecisionOptima(i, actividad.nombre(), "s = " + s,
                    "x = " + x, DinamicaUtils.round(contribucion), "s = " + (s - x)));
            s -= x;
        }

        double valorOptimo = f[1][recurso];
        List<SolveStep> pasos = armarPasos(modelo, sentido, n, recurso, tablas, politica, valorOptimo, s);

        SolucionDinamica solucion = SolucionDinamica.builder()
                .valorOptimo(DinamicaUtils.round(valorOptimo))
                .tablas(tablas)
                .politicaOptima(politica)
                .definicionEtapas("Etapa i = la actividad i (" + n + " en total). Se decide actividad por actividad.")
                .definicionEstados("Estado s = unidades del recurso que quedan disponibles al llegar a la etapa i "
                        + "(s = 0.." + recurso + ").")
                .definicionDecisiones("Decisión x = unidades que se asignan a la actividad de la etapa, "
                        + "con 0 <= x <= s.")
                .funcionRecurrencia(recurrenciaGeneral(sentido, n))
                .principioOptimalidad(DinamicaUtils.PRINCIPIO_OPTIMALIDAD)
                .interpretacionPolitica(interpretacion(politica, valorOptimo, sentido, s))
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private List<SolveStep> armarPasos(ModeloDinamico modelo, SentidoOptimizacion sentido, int n, int recurso,
                                       List<TablaEtapa> tablas, List<DecisionOptima> politica,
                                       double valorOptimo, int sobrante) {
        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Formulación del modelo",
                String.format("Hay %d unidades de recurso para repartir entre %d actividades. "
                        + "Cada etapa es una actividad, el estado es el recurso que queda por repartir y la "
                        + "decisión es cuánto asignarle a la actividad de esa etapa. Se resuelve hacia atrás: "
                        + "%s", recurso, n, DinamicaUtils.PRINCIPIO_OPTIMALIDAD),
                DinamicaUtils.datosFormulacion(METODO,
                        "Etapa i = actividad i (i = 1.." + n + ")",
                        "Estado s = recurso disponible al llegar a la etapa (s = 0.." + recurso + ")",
                        "Decisión x = recurso asignado a la actividad, 0 <= x <= s",
                        recurrenciaGeneral(sentido, n))));

        int numero = 2;
        for (TablaEtapa tabla : tablas) {
            pasos.add(new SolveStep(numero++, tabla.nombreEtapa(),
                    String.format("Se evalúa %s para cada estado s: por cada asignación x admisible se suma el "
                            + "retorno inmediato al valor óptimo ya calculado de la etapa siguiente.",
                            tabla.recurrencia()),
                    DinamicaUtils.datosTabla(METODO, tabla)));
        }

        pasos.add(new SolveStep(numero, "Recuperación de la política óptima",
                String.format("Partiendo del estado inicial s = %d se lee la decisión óptima de cada etapa y se "
                        + "descuenta del recurso disponible. %s El valor óptimo es %s.",
                        recurso, resumenPolitica(politica), DinamicaUtils.fmt(valorOptimo)),
                DinamicaUtils.datosPolitica(METODO, politica, valorOptimo, null)));
        return pasos;
    }

    /** Marca como óptima la evaluación correspondiente a la decisión ganadora. */
    private List<EvaluacionDecision> marcarOptima(List<EvaluacionDecision> evaluaciones, int mejorX) {
        List<EvaluacionDecision> marcadas = new ArrayList<>(evaluaciones.size());
        for (EvaluacionDecision e : evaluaciones) {
            boolean optima = e.decision().equals("x = " + mejorX);
            marcadas.add(new EvaluacionDecision(e.decision(), e.contribucion(), e.valorFuturo(),
                    e.valorTotal(), optima));
        }
        return marcadas;
    }

    private String recurrenciaGeneral(SentidoOptimizacion sentido, int n) {
        String op = sentido == SentidoOptimizacion.MAXIMIZAR ? "max" : "min";
        return String.format("f_i(s) = %s{ r_i(x) + f_(i+1)(s - x) : 0 <= x <= s },  con f_(%d)(s) = 0",
                op, n + 1);
    }

    private String recurrenciaDeEtapa(int i, int n, SentidoOptimizacion sentido, String nombre) {
        String op = sentido == SentidoOptimizacion.MAXIMIZAR ? "max" : "min";
        if (i == n) {
            return String.format("f_%d(s) = %s{ r_%d(x) : 0 <= x <= s }   [%s; la etapa siguiente vale 0]",
                    i, op, i, nombre);
        }
        return String.format("f_%d(s) = %s{ r_%d(x) + f_%d(s - x) : 0 <= x <= s }   [%s]",
                i, op, i, i + 1, nombre);
    }

    private String resumenPolitica(List<DecisionOptima> politica) {
        StringBuilder sb = new StringBuilder();
        for (DecisionOptima d : politica) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(d.nombreEtapa()).append(" recibe ").append(d.decision().replace("x = ", ""))
              .append(" (retorno ").append(DinamicaUtils.fmt(d.contribucion())).append(")");
        }
        return sb.toString() + ".";
    }

    private String interpretacion(List<DecisionOptima> politica, double valorOptimo,
                                  SentidoOptimizacion sentido, int sobrante) {
        String verbo = sentido == SentidoOptimizacion.MAXIMIZAR ? "máximo" : "mínimo";
        StringBuilder sb = new StringBuilder("Reparte el recurso así: ");
        sb.append(resumenPolitica(politica));
        sb.append(String.format(" El retorno total %s es %s.", verbo, DinamicaUtils.fmt(valorOptimo)));
        if (sobrante > 0) {
            sb.append(String.format(" Quedan %d unidades sin asignar: asignarlas no mejoraría el resultado.",
                    sobrante));
        }
        return sb.toString();
    }
}
