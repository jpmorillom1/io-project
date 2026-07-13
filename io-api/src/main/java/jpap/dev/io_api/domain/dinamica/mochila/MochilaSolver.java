package jpap.dev.io_api.domain.dinamica.mochila;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.dinamica.ArticuloMochila;
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
import java.util.List;

/**
 * Mochila entera por programación dinámica: seleccionar artículos (o proyectos, o inversiones)
 * que consumen un recurso limitado, maximizando el valor total.
 *
 * Etapa i   → artículo i.
 * Estado s  → capacidad todavía disponible al llegar a la etapa i.
 * Decisión x→ cuántas unidades del artículo i se llevan, 0 &lt;= x &lt;= min(u_i, s / p_i).
 *
 * Recurrencia hacia atrás, con n artículos:
 *   f_i(s) = max_x [ v_i · x + f_(i+1)(s - p_i · x) ],   f_(n+1)(s) = 0
 *
 * Con {@code unidadesMaximas} = 1 (el default) es la mochila 0/1 clásica. Nunca es infactible:
 * x = 0 siempre es admisible.
 */
public class MochilaSolver {

    private static final MetodoDinamico METODO = MetodoDinamico.MOCHILA;

    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        DinamicaValidador.validarMochila(modelo);

        List<ArticuloMochila> articulos = modelo.articulos();
        int n = articulos.size();
        int capacidad = modelo.capacidad();

        double[][] f = new double[n + 2][capacidad + 1];
        int[][] decision = new int[n + 2][capacidad + 1];
        List<TablaEtapa> tablas = new ArrayList<>();

        for (int i = n; i >= 1; i--) {
            ArticuloMochila articulo = articulos.get(i - 1);
            int maxUnidades = articulo.unidadesMaximasOrDefault();
            List<FilaEtapa> filas = new ArrayList<>();

            for (int s = 0; s <= capacidad; s++) {
                int tope = Math.min(maxUnidades, s / articulo.peso());
                double mejorValor = 0;
                int mejorX = 0;
                List<EvaluacionDecision> evaluaciones = new ArrayList<>();

                for (int x = 0; x <= tope; x++) {
                    double contribucion = articulo.valor() * x;
                    double futuro = f[i + 1][s - articulo.peso() * x];
                    double total = contribucion + futuro;
                    evaluaciones.add(new EvaluacionDecision("x = " + x,
                            DinamicaUtils.round(contribucion), DinamicaUtils.round(futuro),
                            DinamicaUtils.round(total), false));
                    if (total > mejorValor + 1e-9) {
                        mejorValor = total;
                        mejorX = x;
                    }
                }

                f[i][s] = mejorValor;
                decision[i][s] = mejorX;
                filas.add(new FilaEtapa("s = " + s, marcarOptima(evaluaciones, mejorX),
                        "x = " + mejorX, DinamicaUtils.round(mejorValor)));
            }

            tablas.add(new TablaEtapa(i, "Etapa " + i + " — " + articulo.nombre(),
                    recurrenciaDeEtapa(i, n, articulo), filas));
        }

        List<DecisionOptima> politica = new ArrayList<>();
        List<String> seleccionados = new ArrayList<>();
        int s = capacidad;
        for (int i = 1; i <= n; i++) {
            ArticuloMochila articulo = articulos.get(i - 1);
            int x = decision[i][s];
            int siguiente = s - articulo.peso() * x;
            politica.add(new DecisionOptima(i, articulo.nombre(), "s = " + s,
                    "x = " + x, DinamicaUtils.round(articulo.valor() * x), "s = " + siguiente));
            if (x > 0) {
                seleccionados.add(x == 1 ? articulo.nombre() : x + " x " + articulo.nombre());
            }
            s = siguiente;
        }

        double valorOptimo = f[1][capacidad];
        List<SolveStep> pasos = armarPasos(n, capacidad, tablas, politica, valorOptimo, seleccionados, s);

        SolucionDinamica solucion = SolucionDinamica.builder()
                .valorOptimo(DinamicaUtils.round(valorOptimo))
                .tablas(tablas)
                .politicaOptima(politica)
                .definicionEtapas("Etapa i = el artículo i (" + n + " en total). Se decide artículo por artículo.")
                .definicionEstados("Estado s = capacidad que queda libre al llegar a la etapa i (s = 0.."
                        + capacidad + ").")
                .definicionDecisiones("Decisión x = unidades del artículo que se cargan, limitadas por su tope y "
                        + "por la capacidad restante.")
                .funcionRecurrencia(recurrenciaGeneral(n))
                .principioOptimalidad(DinamicaUtils.PRINCIPIO_OPTIMALIDAD)
                .interpretacionPolitica(interpretacion(seleccionados, valorOptimo, capacidad, s))
                .build();

        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    private List<SolveStep> armarPasos(int n, int capacidad, List<TablaEtapa> tablas,
                                       List<DecisionOptima> politica, double valorOptimo,
                                       List<String> seleccionados, int sobrante) {
        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(1, "Formulación del modelo",
                String.format("Hay %d artículos y una capacidad de %d. Cada etapa es un artículo, el estado es la "
                        + "capacidad que queda libre y la decisión es cuántas unidades cargar. Se resuelve hacia "
                        + "atrás: %s", n, capacidad, DinamicaUtils.PRINCIPIO_OPTIMALIDAD),
                DinamicaUtils.datosFormulacion(METODO,
                        "Etapa i = artículo i (i = 1.." + n + ")",
                        "Estado s = capacidad libre al llegar a la etapa (s = 0.." + capacidad + ")",
                        "Decisión x = unidades del artículo cargadas, 0 <= x <= min(u_i, s / p_i)",
                        recurrenciaGeneral(n))));

        int numero = 2;
        for (TablaEtapa tabla : tablas) {
            pasos.add(new SolveStep(numero++, tabla.nombreEtapa(),
                    String.format("Se evalúa %s para cada capacidad restante s: solo son admisibles las x que "
                            + "caben, y a cada una se le suma el valor óptimo ya calculado de la etapa siguiente.",
                            tabla.recurrencia()),
                    DinamicaUtils.datosTabla(METODO, tabla)));
        }

        pasos.add(new SolveStep(numero, "Recuperación de la política óptima",
                String.format("Partiendo del estado inicial s = %d se lee la decisión óptima de cada etapa y se "
                        + "descuenta el peso cargado. Se llevan: %s. El valor máximo es %s y sobran %d unidades "
                        + "de capacidad.",
                        capacidad, seleccionados.isEmpty() ? "ningún artículo" : String.join(", ", seleccionados),
                        DinamicaUtils.fmt(valorOptimo), sobrante),
                DinamicaUtils.datosPolitica(METODO, politica, valorOptimo, null)));
        return pasos;
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

    private String recurrenciaGeneral(int n) {
        return String.format("f_i(s) = max{ v_i · x + f_(i+1)(s - p_i · x) : 0 <= x <= min(u_i, s / p_i) },  "
                + "con f_(%d)(s) = 0", n + 1);
    }

    private String recurrenciaDeEtapa(int i, int n, ArticuloMochila a) {
        String cabecera = String.format("f_%d(s) = max{ %s · x", i, DinamicaUtils.fmt(a.valor()));
        String cola = String.format(" : 0 <= x <= min(%d, s / %d) }   [%s]",
                a.unidadesMaximasOrDefault(), a.peso(), a.nombre());
        if (i == n) {
            return cabecera + cola + "  [la etapa siguiente vale 0]";
        }
        return cabecera + String.format(" + f_%d(s - %d · x)", i + 1, a.peso()) + cola;
    }

    private String interpretacion(List<String> seleccionados, double valorOptimo, int capacidad, int sobrante) {
        if (seleccionados.isEmpty()) {
            return "Ningún artículo cabe o ninguno aporta valor: la mochila se queda vacía.";
        }
        return String.format("Carga %s. Consumes %d de las %d unidades de capacidad (sobran %d) y obtienes un "
                + "valor total de %s, el máximo alcanzable. Fíjate en que el criterio NO es el mayor valor "
                + "unitario: es el mejor uso de la capacidad como un todo.",
                String.join(" y ", seleccionados), capacidad - sobrante, capacidad, sobrante,
                DinamicaUtils.fmt(valorOptimo));
    }
}
