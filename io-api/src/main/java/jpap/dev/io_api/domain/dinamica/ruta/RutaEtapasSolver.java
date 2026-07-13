package jpap.dev.io_api.domain.dinamica.ruta;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.dinamica.ArcoRuta;
import jpap.dev.io_api.domain.dinamica.DecisionOptima;
import jpap.dev.io_api.domain.dinamica.DinamicaUtils;
import jpap.dev.io_api.domain.dinamica.DinamicaValidador;
import jpap.dev.io_api.domain.dinamica.EtapaRuta;
import jpap.dev.io_api.domain.dinamica.EvaluacionDecision;
import jpap.dev.io_api.domain.dinamica.FilaEtapa;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SentidoOptimizacion;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import jpap.dev.io_api.domain.dinamica.TablaEtapa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ruta secuencial sobre una red por etapas (el clásico problema de la diligencia).
 *
 * Etapa k   → la columna k de la red; se avanza siempre de la etapa k a la k+1.
 * Estado s  → el nodo en el que uno se encuentra dentro de la etapa k.
 * Decisión  → a qué nodo de la etapa k+1 saltar.
 *
 * Recurrencia hacia atrás, con K etapas:
 *   f_k(s) = opt{ c(s, d) + f_(k+1)(d) : existe arco s -&gt; d },   f_K(destino) = 0
 *
 * Si el origen no alcanza ninguna de las salidas de la última etapa el resultado es
 * INFACTIBLE (no una excepción). Los estados inalcanzables no producen fila en la tabla.
 */
public class RutaEtapasSolver {

    private static final MetodoDinamico METODO = MetodoDinamico.RUTA_ETAPAS;

    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        DinamicaValidador.validarRutaEtapas(modelo);

        SentidoOptimizacion sentido = modelo.sentidoOrDefault();
        List<EtapaRuta> etapas = new ArrayList<>(modelo.etapasRuta());
        etapas.sort(Comparator.comparingInt(EtapaRuta::etapa));
        int ultima = etapas.size();
        String origen = etapas.get(0).nodos().get(0);

        Map<String, List<ArcoRuta>> salientes = new HashMap<>();
        for (ArcoRuta arco : modelo.arcos()) {
            salientes.computeIfAbsent(arco.origen(), k -> new ArrayList<>()).add(arco);
        }

        Map<String, Double> f = new HashMap<>();
        Map<String, ArcoRuta> decision = new HashMap<>();
        for (String destino : etapas.get(ultima - 1).nodos()) {
            f.put(destino, 0.0);
        }

        List<TablaEtapa> tablas = new ArrayList<>();
        for (int k = ultima - 1; k >= 1; k--) {
            EtapaRuta etapa = etapas.get(k - 1);
            List<FilaEtapa> filas = new ArrayList<>();

            for (String nodo : etapa.nodos()) {
                double mejorValor = DinamicaUtils.peorValor(sentido);
                ArcoRuta mejorArco = null;
                List<EvaluacionDecision> evaluaciones = new ArrayList<>();

                for (ArcoRuta arco : salientes.getOrDefault(nodo, List.of())) {
                    Double futuro = f.get(arco.destino());
                    if (futuro == null) continue;   // el destino no alcanza la salida: decisión no admisible
                    double total = arco.costo() + futuro;
                    evaluaciones.add(new EvaluacionDecision("ir a " + arco.destino(),
                            DinamicaUtils.round(arco.costo()), DinamicaUtils.round(futuro),
                            DinamicaUtils.round(total), false));
                    if (mejorArco == null || DinamicaUtils.esMejor(sentido, total, mejorValor)) {
                        mejorValor = total;
                        mejorArco = arco;
                    }
                }

                if (mejorArco == null) continue;    // nodo inalcanzable hacia adelante: no genera fila
                f.put(nodo, mejorValor);
                decision.put(nodo, mejorArco);
                filas.add(new FilaEtapa(nodo, marcarOptima(evaluaciones, "ir a " + mejorArco.destino()),
                        "ir a " + mejorArco.destino(), DinamicaUtils.round(mejorValor)));
            }

            tablas.add(new TablaEtapa(k, "Etapa " + k, recurrenciaDeEtapa(k, ultima, sentido), filas));
        }

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(pasoFormulacion(modelo, sentido, ultima, origen));
        int numero = 2;
        for (TablaEtapa tabla : tablas) {
            pasos.add(new SolveStep(numero++, tabla.nombreEtapa(),
                    String.format("Se evalúa %s para cada nodo de la etapa: se compara el costo de cada arco "
                            + "saliente más el valor óptimo ya calculado del nodo al que lleva.", tabla.recurrencia()),
                    DinamicaUtils.datosTabla(METODO, tabla)));
        }

        if (!f.containsKey(origen)) {
            pasos.add(new SolveStep(numero, "Sin ruta al destino",
                    String.format("Desde el origen '%s' no existe ninguna secuencia de arcos que llegue a la "
                            + "etapa %d. El problema es infactible: revisa los arcos de la red.", origen, ultima),
                    DinamicaUtils.datos(METODO)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        // Recuperación de la ruta hacia adelante desde el origen.
        List<DecisionOptima> politica = new ArrayList<>();
        List<String> ruta = new ArrayList<>();
        ruta.add(origen);
        String actual = origen;
        for (int k = 1; k <= ultima - 1; k++) {
            ArcoRuta arco = decision.get(actual);
            politica.add(new DecisionOptima(k, "Etapa " + k, actual, "ir a " + arco.destino(),
                    DinamicaUtils.round(arco.costo()), arco.destino()));
            ruta.add(arco.destino());
            actual = arco.destino();
        }

        double valorOptimo = f.get(origen);
        pasos.add(new SolveStep(numero, "Recuperación de la política óptima",
                String.format("Desde el origen '%s' se sigue en cada etapa el arco marcado como óptimo. "
                        + "La ruta es %s, con un valor total de %s.",
                        origen, String.join(" -> ", ruta), DinamicaUtils.fmt(valorOptimo)),
                DinamicaUtils.datosPolitica(METODO, politica, valorOptimo, ruta)));

        SolucionDinamica solucion = SolucionDinamica.builder()
                .valorOptimo(DinamicaUtils.round(valorOptimo))
                .tablas(tablas)
                .politicaOptima(politica)
                .rutaOptima(ruta)
                .definicionEtapas("Etapa k = la columna k de la red (" + ultima + " en total); cada arco avanza "
                        + "exactamente una etapa.")
                .definicionEstados("Estado s = el nodo en el que uno se encuentra dentro de la etapa k.")
                .definicionDecisiones("Decisión = a qué nodo de la etapa k+1 saltar desde el nodo actual.")
                .funcionRecurrencia(recurrenciaGeneral(sentido, ultima))
                .principioOptimalidad(DinamicaUtils.PRINCIPIO_OPTIMALIDAD)
                .interpretacionPolitica(String.format(
                        "Recorre %s. El valor total de la ruta es %s, el %s posible. Cualquier tramo de esta ruta "
                        + "es a su vez la mejor ruta entre sus extremos: eso es exactamente el principio de "
                        + "optimalidad en acción.",
                        String.join(" -> ", ruta), DinamicaUtils.fmt(valorOptimo),
                        sentido == SentidoOptimizacion.MINIMIZAR ? "mínimo" : "máximo"))
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private SolveStep pasoFormulacion(ModeloDinamico modelo, SentidoOptimizacion sentido, int ultima, String origen) {
        return new SolveStep(1, "Formulación del modelo",
                String.format("La red tiene %d etapas y %d arcos. Se parte del nodo '%s' y hay que llegar a la "
                        + "etapa %d %s el valor acumulado. Se resuelve hacia atrás: la condición de frontera es "
                        + "f_%d(destino) = 0. %s",
                        ultima, modelo.arcos().size(), origen, ultima,
                        sentido == SentidoOptimizacion.MINIMIZAR ? "minimizando" : "maximizando",
                        ultima, DinamicaUtils.PRINCIPIO_OPTIMALIDAD),
                DinamicaUtils.datosFormulacion(METODO,
                        "Etapa k = columna k de la red (k = 1.." + ultima + ")",
                        "Estado s = nodo actual dentro de la etapa k",
                        "Decisión = nodo de la etapa k+1 al que se salta",
                        recurrenciaGeneral(sentido, ultima)));
    }

    private List<EvaluacionDecision> marcarOptima(List<EvaluacionDecision> evaluaciones, String mejorDecision) {
        List<EvaluacionDecision> marcadas = new ArrayList<>(evaluaciones.size());
        for (EvaluacionDecision e : evaluaciones) {
            marcadas.add(new EvaluacionDecision(e.decision(), e.contribucion(), e.valorFuturo(),
                    e.valorTotal(), e.decision().equals(mejorDecision)));
        }
        return marcadas;
    }

    private String recurrenciaGeneral(SentidoOptimizacion sentido, int ultima) {
        String op = sentido == SentidoOptimizacion.MAXIMIZAR ? "max" : "min";
        return String.format("f_k(s) = %s{ c(s, d) + f_(k+1)(d) : existe arco s -> d },  con f_%d(destino) = 0",
                op, ultima);
    }

    private String recurrenciaDeEtapa(int k, int ultima, SentidoOptimizacion sentido) {
        String op = sentido == SentidoOptimizacion.MAXIMIZAR ? "max" : "min";
        if (k == ultima - 1) {
            return String.format("f_%d(s) = %s{ c(s, d) }   [la etapa %d es el destino, f_%d(d) = 0]",
                    k, op, ultima, ultima);
        }
        return String.format("f_%d(s) = %s{ c(s, d) + f_%d(d) }", k, op, k + 1);
    }
}
