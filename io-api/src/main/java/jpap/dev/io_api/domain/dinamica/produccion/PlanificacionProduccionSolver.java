package jpap.dev.io_api.domain.dinamica.produccion;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
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
 * Planificación de producción (y de inventarios) por etapas: decidir cuánto producir en cada
 * periodo para cubrir una demanda conocida al mínimo costo total.
 *
 * Etapa t   → el periodo t.
 * Estado i  → unidades en inventario al INICIO del periodo t.
 * Decisión x→ unidades a producir en el periodo t.
 *
 * El inventario al final del periodo es j = i + x - d_t, y debe cumplir 0 &lt;= j &lt;= capacidad de
 * almacén (no se admiten faltantes). El costo del periodo es la preparación K si se produce
 * algo, más el costo unitario c por cada unidad, más el costo h de mantener el inventario final.
 *
 * Recurrencia hacia atrás, con T periodos:
 *   f_t(i) = min_x [ K·[x &gt; 0] + c·x + h·(i + x - d_t) + f_(t+1)(i + x - d_t) ]
 *   f_(T+1)(i) = 0 si i es el inventario final exigido, e infinito en otro caso
 *
 * Si la capacidad de producción no alcanza a cubrir la demanda el resultado es INFACTIBLE
 * (no una excepción). Los estados inalcanzables no producen fila en la tabla.
 */
public class PlanificacionProduccionSolver {

    private static final MetodoDinamico METODO = MetodoDinamico.PLANIFICACION_PRODUCCION;
    private static final double INFINITO = Double.POSITIVE_INFINITY;

    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        DinamicaValidador.validarPlanificacionProduccion(modelo);

        List<Integer> demandas = modelo.demandas();
        int periodos = demandas.size();
        int inventarioInicial = modelo.inventarioInicialOrDefault();
        int inventarioFinal = modelo.inventarioFinalOrDefault();
        double preparacion = modelo.costoPreparacion();
        double unitario = modelo.costoUnitarioProduccion();
        double mantener = modelo.costoMantener();

        int demandaTotal = demandas.stream().mapToInt(Integer::intValue).sum();
        int topeAlmacen = modelo.capacidadAlmacen() != null
                ? modelo.capacidadAlmacen()
                : Math.max(inventarioInicial, demandaTotal + inventarioFinal);
        int topeProduccion = modelo.capacidadProduccion() != null
                ? modelo.capacidadProduccion()
                : demandaTotal + inventarioFinal;

        // Estados alcanzables hacia adelante desde el inventario inicial (mantiene las tablas pequeñas).
        List<TreeSet<Integer>> alcanzables = estadosAlcanzables(demandas, inventarioInicial,
                topeAlmacen, topeProduccion);

        // Recursión hacia atrás.
        List<Map<Integer, Double>> f = new ArrayList<>();
        List<Map<Integer, Integer>> decision = new ArrayList<>();
        for (int t = 0; t <= periodos + 1; t++) {
            f.add(new HashMap<>());
            decision.add(new HashMap<>());
        }
        for (int i : alcanzables.get(periodos)) {
            f.get(periodos + 1).put(i, i == inventarioFinal ? 0.0 : INFINITO);
        }

        List<TablaEtapa> tablas = new ArrayList<>();
        for (int t = periodos; t >= 1; t--) {
            int demanda = demandas.get(t - 1);
            List<FilaEtapa> filas = new ArrayList<>();

            for (int i : alcanzables.get(t - 1)) {
                double mejorValor = INFINITO;
                int mejorX = -1;
                List<EvaluacionDecision> evaluaciones = new ArrayList<>();

                for (int x = 0; x <= topeProduccion; x++) {
                    int finalPeriodo = i + x - demanda;
                    if (finalPeriodo < 0 || finalPeriodo > topeAlmacen) continue;
                    Double futuro = f.get(t + 1).get(finalPeriodo);
                    if (futuro == null || DinamicaUtils.inalcanzable(futuro)) continue;

                    double contribucion = (x > 0 ? preparacion : 0) + unitario * x + mantener * finalPeriodo;
                    double total = contribucion + futuro;
                    evaluaciones.add(new EvaluacionDecision("x = " + x,
                            DinamicaUtils.round(contribucion), DinamicaUtils.round(futuro),
                            DinamicaUtils.round(total), false));
                    if (total < mejorValor - 1e-9) {
                        mejorValor = total;
                        mejorX = x;
                    }
                }

                if (mejorX < 0) continue;   // desde este inventario no se puede cerrar el horizonte
                f.get(t).put(i, mejorValor);
                decision.get(t).put(i, mejorX);
                filas.add(new FilaEtapa("i = " + i, marcarOptima(evaluaciones, mejorX),
                        "x = " + mejorX, DinamicaUtils.round(mejorValor)));
            }

            tablas.add(new TablaEtapa(t, "Etapa " + t + " — periodo " + t + " (demanda " + demanda + ")",
                    recurrenciaDeEtapa(t, periodos, demanda), filas));
        }

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(pasoFormulacion(periodos, demandas, preparacion, unitario, mantener,
                inventarioInicial, inventarioFinal));
        int numero = 2;
        for (TablaEtapa tabla : tablas) {
            pasos.add(new SolveStep(numero++, tabla.nombreEtapa(),
                    String.format("Se evalúa %s para cada inventario inicial i alcanzable: por cada cantidad x a "
                            + "producir se suma el costo del periodo al costo óptimo ya calculado del resto del "
                            + "horizonte.", tabla.recurrencia()),
                    DinamicaUtils.datosTabla(METODO, tabla)));
        }

        Double optimo = f.get(1).get(inventarioInicial);
        if (optimo == null || DinamicaUtils.inalcanzable(optimo)) {
            pasos.add(new SolveStep(numero, "Plan de producción infactible",
                    String.format("Partiendo de un inventario inicial de %d unidades no existe ningún plan que "
                            + "cubra la demanda de los %d periodos sin faltantes y termine con %d unidades. "
                            + "Revisa la capacidad de producción o la de almacén.",
                            inventarioInicial, periodos, inventarioFinal),
                    DinamicaUtils.datos(METODO)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        // Recuperación del plan hacia adelante.
        List<DecisionOptima> politica = new ArrayList<>();
        int inventario = inventarioInicial;
        for (int t = 1; t <= periodos; t++) {
            int x = decision.get(t).get(inventario);
            int demanda = demandas.get(t - 1);
            int finalPeriodo = inventario + x - demanda;
            double contribucion = (x > 0 ? preparacion : 0) + unitario * x + mantener * finalPeriodo;
            politica.add(new DecisionOptima(t, "Periodo " + t, "i = " + inventario, "x = " + x,
                    DinamicaUtils.round(contribucion), "i = " + finalPeriodo));
            inventario = finalPeriodo;
        }

        pasos.add(new SolveStep(numero, "Recuperación de la política óptima",
                String.format("Partiendo del inventario inicial i = %d se lee la producción óptima de cada "
                        + "periodo. El plan es %s, con un costo total de %s.",
                        inventarioInicial, resumenPlan(politica), DinamicaUtils.fmt(optimo)),
                DinamicaUtils.datosPolitica(METODO, politica, optimo, null)));

        SolucionDinamica solucion = SolucionDinamica.builder()
                .valorOptimo(DinamicaUtils.round(optimo))
                .tablas(tablas)
                .politicaOptima(politica)
                .definicionEtapas("Etapa t = el periodo t (" + periodos + " en total).")
                .definicionEstados("Estado i = unidades en inventario al inicio del periodo t "
                        + "(0 <= i <= " + topeAlmacen + ").")
                .definicionDecisiones("Decisión x = unidades a producir en el periodo t"
                        + (modelo.capacidadProduccion() != null
                            ? ", con 0 <= x <= " + modelo.capacidadProduccion() : "")
                        + ". El inventario final del periodo es i + x - d_t y no puede ser negativo.")
                .funcionRecurrencia(recurrenciaGeneral(periodos, preparacion, unitario, mantener, inventarioFinal))
                .principioOptimalidad(DinamicaUtils.PRINCIPIO_OPTIMALIDAD)
                .interpretacionPolitica(String.format(
                        "Produce %s. El costo total mínimo es %s. Observa el trade-off: agrupar la producción "
                        + "en pocos lotes ahorra costos de preparación pero obliga a mantener inventario; "
                        + "producir en cada periodo evita el inventario pero paga la preparación cada vez.",
                        resumenPlan(politica), DinamicaUtils.fmt(optimo)))
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    /** Inventarios iniciales posibles en cada periodo, propagando hacia adelante desde el inicial. */
    private List<TreeSet<Integer>> estadosAlcanzables(List<Integer> demandas, int inventarioInicial,
                                                      int topeAlmacen, int topeProduccion) {
        int periodos = demandas.size();
        List<TreeSet<Integer>> alcanzables = new ArrayList<>();
        for (int t = 0; t <= periodos; t++) alcanzables.add(new TreeSet<>());
        alcanzables.get(0).add(inventarioInicial);

        for (int t = 1; t <= periodos; t++) {
            int demanda = demandas.get(t - 1);
            for (int i : alcanzables.get(t - 1)) {
                for (int x = 0; x <= topeProduccion; x++) {
                    int finalPeriodo = i + x - demanda;
                    if (finalPeriodo < 0 || finalPeriodo > topeAlmacen) continue;
                    alcanzables.get(t).add(finalPeriodo);
                }
            }
        }
        return alcanzables;
    }

    private SolveStep pasoFormulacion(int periodos, List<Integer> demandas, double preparacion,
                                      double unitario, double mantener, int inventarioInicial,
                                      int inventarioFinal) {
        return new SolveStep(1, "Formulación del modelo",
                String.format("Horizonte de %d periodos con demandas %s. Preparar un lote cuesta %s, cada unidad "
                        + "producida cuesta %s y mantener una unidad un periodo cuesta %s. Se arranca con %d "
                        + "unidades y se debe terminar con %d. Cada etapa es un periodo, el estado es el "
                        + "inventario con el que se llega y la decisión es cuánto producir. %s",
                        periodos, demandas, DinamicaUtils.fmt(preparacion), DinamicaUtils.fmt(unitario),
                        DinamicaUtils.fmt(mantener), inventarioInicial, inventarioFinal,
                        DinamicaUtils.PRINCIPIO_OPTIMALIDAD),
                DinamicaUtils.datosFormulacion(METODO,
                        "Etapa t = periodo t (t = 1.." + periodos + ")",
                        "Estado i = inventario al inicio del periodo t",
                        "Decisión x = unidades producidas en el periodo t",
                        recurrenciaGeneral(periodos, preparacion, unitario, mantener, inventarioFinal)));
    }

    private List<EvaluacionDecision> marcarOptima(List<EvaluacionDecision> evaluaciones, int mejorX) {
        List<EvaluacionDecision> marcadas = new ArrayList<>(evaluaciones.size());
        for (EvaluacionDecision e : evaluaciones) {
            boolean optima = e.decision().equals("x = " + mejorX);
            marcadas.add(new EvaluacionDecision(e.decision(), e.contribucion(), e.valorFuturo(),
                    e.valorTotal(), optima));
        }
        return marcadas;
    }

    private String recurrenciaGeneral(int periodos, double preparacion, double unitario, double mantener,
                                      int inventarioFinal) {
        return String.format("f_t(i) = min{ %s·[x > 0] + %s·x + %s·(i + x - d_t) + f_(t+1)(i + x - d_t) },  "
                + "con f_(%d)(i) = 0 si i = %d e infinito en otro caso",
                DinamicaUtils.fmt(preparacion), DinamicaUtils.fmt(unitario), DinamicaUtils.fmt(mantener),
                periodos + 1, inventarioFinal);
    }

    private String recurrenciaDeEtapa(int t, int periodos, int demanda) {
        if (t == periodos) {
            return String.format("f_%d(i) = min{ costo del periodo con d_%d = %d }   "
                    + "[última etapa: solo son admisibles las x que dejan el inventario final exigido]",
                    t, t, demanda);
        }
        return String.format("f_%d(i) = min{ costo del periodo con d_%d = %d, + f_%d(i + x - %d) }",
                t, t, demanda, t + 1, demanda);
    }

    private String resumenPlan(List<DecisionOptima> politica) {
        StringBuilder sb = new StringBuilder();
        for (DecisionOptima d : politica) {
            if (sb.length() > 0) sb.append("; ");
            String x = d.decision().replace("x = ", "");
            sb.append(d.nombreEtapa()).append(": ")
              .append("0".equals(x) ? "no producir" : x + " unidades");
        }
        return sb.toString();
    }
}
