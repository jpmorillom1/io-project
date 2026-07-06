package jpap.dev.io_api.domain.redes.flujocostominimo;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.RedUtils;
import jpap.dev.io_api.domain.redes.RedValidador;
import jpap.dev.io_api.domain.redes.SolucionRed;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flujo máximo de costo mínimo fuente→sumidero por successive shortest paths:
 * en cada iteración se busca el camino de MENOR COSTO en la red residual con
 * Bellman-Ford/SPFA (los arcos inversos tienen costo negativo, por eso no sirve
 * Dijkstra) y se aumenta por el cuello de botella, hasta que no quede camino.
 *
 * El core ({@link #ejecutarCore}) recibe la red ya construida y es reusado por
 * AsignacionSolver, que reduce la asignación a una red bipartita unitaria.
 *
 * Sin ningún camino fuente→sumidero → SolveStatus.INFACTIBLE.
 *
 * Java puro, sin dependencias de framework.
 */
public class FlujoCostoMinimoSolver {

    private static final double EPS = 1e-9;

    /** Red ya construida sobre la que corre el core (la usa también AsignacionSolver). */
    public record RedMcf(List<String> nodos, List<Arista> arcos, String fuente, String sumidero) {}

    /** Resultado del core: flujo máximo alcanzado, costo total mínimo y flujo neto por arco. */
    public record ResultadoMcf(
            double flujoTotal,
            double costoTotal,
            Map<String, Double> flujoPorArco,
            List<Arista> arcosConFlujo
    ) {}

    /** Arco de la red residual. Los inversos tienen original == null, residual 0 y costo −c. */
    private static final class Arco {
        final Arista original;
        final String desde;
        final String hacia;
        final double costo;
        double residual;
        Arco inverso;

        Arco(Arista original, String desde, String hacia, double costo, double residual) {
            this.original = original;
            this.desde = desde;
            this.hacia = hacia;
            this.costo = costo;
            this.residual = residual;
        }

        double flujo() {
            return original != null ? inverso.residual : 0.0;
        }
    }

    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        RedValidador.validar(modelo);

        List<SolveStep> pasos = new ArrayList<>();
        pasos.add(new SolveStep(0, "Red residual inicial (capacidad y costo)",
                "Se busca el flujo máximo de COSTO MÍNIMO de '" + modelo.fuente() + "' a '" + modelo.sumidero()
                        + "'. Cada arco directo entra con (capacidad, costo) y su inverso con (0, −costo). "
                        + "En cada iteración, Bellman-Ford halla el camino más barato en la red residual "
                        + "y se aumenta por su cuello de botella.",
                RedUtils.datosBase(modelo)));

        ResultadoMcf r = ejecutarCore(
                new RedMcf(modelo.nodos(), modelo.aristas(), modelo.fuente(), modelo.sumidero()),
                modelo.metodo(), pasos);

        if (r.flujoTotal() <= EPS) {
            pasos.add(new SolveStep(pasos.size(), "No existe camino fuente→sumidero",
                    "No hay ningún camino con capacidad disponible de '" + modelo.fuente() + "' a '"
                            + modelo.sumidero() + "': el problema es infactible.",
                    RedUtils.datosBase(modelo)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        SolucionRed solucion = new SolucionRed(null, null, r.arcosConFlujo(), r.flujoPorArco(), null,
                RedUtils.round(r.costoTotal()), RedUtils.round(r.flujoTotal()), RedUtils.round(r.costoTotal()));
        return new SolveResult<>(SolveStatus.OPTIMO, solucion, pasos);
    }

    /**
     * Core reusable: corre successive shortest paths sobre la red dada y registra en
     * `pasos` un paso por camino aumentante más el paso final de resumen.
     * `metodoParaPasos` etiqueta el mapa `datos` (FLUJO_COSTO_MINIMO o ASIGNACION).
     */
    public ResultadoMcf ejecutarCore(RedMcf red, MetodoRed metodoParaPasos, List<SolveStep> pasos) {
        Map<String, List<Arco>> ady = new HashMap<>();
        for (String n : red.nodos()) ady.put(n, new ArrayList<>());
        List<Arco> directos = new ArrayList<>(red.arcos().size());
        for (Arista a : red.arcos()) {
            Arco directo = new Arco(a, a.origen(), a.destino(), a.costo(), a.capacidad());
            Arco inverso = new Arco(null, a.destino(), a.origen(), -a.costo(), 0.0);
            directo.inverso = inverso;
            inverso.inverso = directo;
            ady.get(a.origen()).add(directo);
            ady.get(a.destino()).add(inverso);
            directos.add(directo);
        }

        double flujoTotal = 0.0;
        double costoTotal = 0.0;
        int iteracion = 0;

        while (true) {
            Map<String, Arco> llegada = caminoMasBarato(red, ady);
            if (!llegada.containsKey(red.sumidero())) break;

            List<Arco> camino = new ArrayList<>();
            String actual = red.sumidero();
            double costoUnitario = 0.0;
            while (!actual.equals(red.fuente())) {
                Arco arco = llegada.get(actual);
                camino.add(0, arco);
                costoUnitario += arco.costo;
                actual = arco.desde;
            }

            double cuello = Double.MAX_VALUE;
            for (Arco arco : camino) cuello = Math.min(cuello, arco.residual);
            for (Arco arco : camino) {
                arco.residual -= cuello;
                arco.inverso.residual += cuello;
            }
            flujoTotal += cuello;
            costoTotal += cuello * costoUnitario;
            iteracion++;

            List<String> nodosCamino = new ArrayList<>();
            nodosCamino.add(red.fuente());
            for (Arco arco : camino) nodosCamino.add(arco.hacia);
            boolean usaInverso = camino.stream().anyMatch(arco -> arco.original == null);

            Map<String, Object> datos = datosPaso(metodoParaPasos, red, directos, camino);
            datos.put("camino", nodosCamino);
            datos.put("costoUnitario", RedUtils.round(costoUnitario));
            datos.put("cuelloBotella", RedUtils.round(cuello));
            datos.put("flujoTotal", RedUtils.round(flujoTotal));
            datos.put("costoAcumulado", RedUtils.round(costoTotal));
            pasos.add(new SolveStep(pasos.size(),
                    "Camino aumentante " + iteracion + ": " + String.join(" → ", nodosCamino)
                            + " (costo unitario " + RedUtils.round(costoUnitario) + ")",
                    "Es el camino más barato en la red residual. Cuello de botella = " + RedUtils.round(cuello)
                            + ": se envían " + RedUtils.round(cuello) + " unidad(es) con costo "
                            + RedUtils.round(cuello * costoUnitario) + ". Flujo acumulado = "
                            + RedUtils.round(flujoTotal) + "; costo acumulado = " + RedUtils.round(costoTotal) + "."
                            + (usaInverso ? " El camino usa un arco inverso: se cancela flujo previo (re-ruteo más barato)." : ""),
                    datos));
        }

        Map<String, Double> flujoPorArco = new LinkedHashMap<>();
        List<Arista> conFlujo = new ArrayList<>();
        for (Arco arco : directos) {
            if (arco.flujo() > EPS) {
                flujoPorArco.merge(RedUtils.claveArco(arco.desde, arco.hacia),
                        RedUtils.round(arco.flujo()), Double::sum);
                conFlujo.add(arco.original);
            }
        }

        if (flujoTotal > EPS) {
            Map<String, Object> datosF = datosPaso(metodoParaPasos, red, directos, List.of());
            datosF.put("flujoTotal", RedUtils.round(flujoTotal));
            datosF.put("costoTotal", RedUtils.round(costoTotal));
            pasos.add(new SolveStep(pasos.size(), "Flujo de costo mínimo alcanzado",
                    "Ya no queda camino en la red residual: flujo máximo = " + RedUtils.round(flujoTotal)
                            + " con costo total mínimo = " + RedUtils.round(costoTotal)
                            + " (caminos aumentantes: " + iteracion + ").",
                    datosF));
        }

        return new ResultadoMcf(flujoTotal, costoTotal, flujoPorArco, conFlujo);
    }

    // ─── internos ────────────────────────────────────────────────────────────────

    /**
     * SPFA (Bellman-Ford con cola): camino de costo mínimo fuente→sumidero sobre arcos
     * con residual > 0. Maneja los costos negativos de los arcos inversos.
     */
    private Map<String, Arco> caminoMasBarato(RedMcf red, Map<String, List<Arco>> ady) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, Arco> llegada = new HashMap<>();
        Set<String> enCola = new HashSet<>();
        Deque<String> cola = new ArrayDeque<>();

        dist.put(red.fuente(), 0.0);
        cola.add(red.fuente());
        enCola.add(red.fuente());

        while (!cola.isEmpty()) {
            String nodo = cola.poll();
            enCola.remove(nodo);
            for (Arco arco : ady.get(nodo)) {
                if (arco.residual <= EPS) continue;
                double candidata = dist.get(nodo) + arco.costo;
                Double actual = dist.get(arco.hacia);
                if (actual == null || candidata < actual - EPS) {
                    dist.put(arco.hacia, candidata);
                    llegada.put(arco.hacia, arco);
                    if (enCola.add(arco.hacia)) cola.add(arco.hacia);
                }
            }
        }
        return llegada;
    }

    private Map<String, Object> datosPaso(MetodoRed metodo, RedMcf red,
                                          List<Arco> directos, List<Arco> camino) {
        Set<Arista> activas = new HashSet<>();
        for (Arco arco : camino)
            activas.add(arco.original != null ? arco.original : arco.inverso.original);

        List<Map<String, Object>> aristas = new ArrayList<>(directos.size());
        for (Arco arco : directos) {
            String estado = activas.contains(arco.original) ? RedUtils.ESTADO_ACTIVA
                    : arco.flujo() > EPS ? RedUtils.ESTADO_SOLUCION
                    : RedUtils.ESTADO_NORMAL;
            aristas.add(RedUtils.arista(arco.original, estado, arco.flujo()));
        }
        return RedUtils.datosBase(metodo, red.nodos(), aristas, red.fuente(), red.sumidero());
    }
}
