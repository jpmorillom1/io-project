package jpap.dev.io_api.domain.redes.kruskal;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.RedUtils;
import jpap.dev.io_api.domain.redes.RedValidador;
import jpap.dev.io_api.domain.redes.SolucionRed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Árbol de expansión mínima por Kruskal: ordena las aristas por peso ascendente y
 * acepta cada una que NO forme ciclo (Union-Find), hasta reunir n−1 aristas.
 * El grafo se trata como NO dirigido aunque el modelo diga lo contrario.
 *
 * Grafo desconexo (menos de n−1 aristas aceptadas) → SolveStatus.INFACTIBLE.
 *
 * Java puro, sin dependencias de framework.
 */
public class KruskalSolver {

    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        RedValidador.validar(modelo);

        List<Arista> ordenadas = new ArrayList<>(modelo.aristas());
        ordenadas.sort(Comparator.comparingDouble(Arista::peso));

        List<SolveStep> pasos = new ArrayList<>();
        StringJoiner orden = new StringJoiner(", ");
        for (Arista a : ordenadas)
            orden.add(a.origen() + "—" + a.destino() + " (" + RedUtils.round(a.peso()) + ")");
        pasos.add(new SolveStep(0, "Aristas ordenadas por peso",
                "Kruskal examina las aristas de menor a mayor peso y acepta las que no formen ciclo. "
                        + "Orden de examen: " + orden + ".",
                RedUtils.datosBase(modelo)));

        UnionFind uf = new UnionFind(modelo.nodos());
        List<Arista> arbol = new ArrayList<>();
        List<Arista> rechazadas = new ArrayList<>();
        double pesoAcumulado = 0.0;
        int objetivo = modelo.nodos().size() - 1;

        for (Arista a : ordenadas) {
            if (arbol.size() == objetivo) break;   // el árbol ya está completo

            boolean aceptada = uf.unir(a.origen(), a.destino());
            String etiqueta = a.origen() + "—" + a.destino() + " (peso " + RedUtils.round(a.peso()) + ")";
            if (aceptada) {
                arbol.add(a);
                pesoAcumulado += a.peso();
                pasos.add(new SolveStep(pasos.size(), "Aceptar " + etiqueta,
                        "No forma ciclo: conecta dos componentes distintas. Aristas en el árbol: "
                                + arbol.size() + " de " + objetivo + "; peso acumulado = "
                                + RedUtils.round(pesoAcumulado) + ".",
                        datosPaso(modelo, arbol, rechazadas, a, pesoAcumulado)));
            } else {
                rechazadas.add(a);
                pasos.add(new SolveStep(pasos.size(), "Rechazar " + etiqueta,
                        "Forma un ciclo: '" + a.origen() + "' y '" + a.destino()
                                + "' ya están conectados por el árbol parcial.",
                        datosPaso(modelo, arbol, rechazadas, a, pesoAcumulado)));
            }
        }

        if (arbol.size() < objetivo) {
            pasos.add(new SolveStep(pasos.size(), "El grafo no es conexo",
                    "Se agotaron las aristas con solo " + arbol.size() + " de " + objetivo
                            + " necesarias: hay componentes que ningún camino conecta. "
                            + "No existe árbol de expansión que cubra todos los nodos.",
                    datosPaso(modelo, arbol, rechazadas, null, pesoAcumulado)));
            return new SolveResult<>(SolveStatus.INFACTIBLE, null, pasos);
        }

        double pesoTotal = RedUtils.round(pesoAcumulado);
        Map<String, Object> datosF = datosPaso(modelo, arbol, rechazadas, null, pesoAcumulado);
        datosF.put("pesoTotal", pesoTotal);
        pasos.add(new SolveStep(pasos.size(), "Árbol de expansión mínima completo",
                "Con " + arbol.size() + " arista(s) quedaron conectados los " + modelo.nodos().size()
                        + " nodos. Peso total del árbol = " + pesoTotal + ".",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                new SolucionRed(null, null, List.copyOf(arbol), null, null, pesoTotal, null, null),
                pasos);
    }

    private Map<String, Object> datosPaso(ModeloRed modelo, List<Arista> arbol,
                                          List<Arista> rechazadas, Arista evaluada,
                                          double pesoAcumulado) {
        Set<Arista> enArbol = new HashSet<>(arbol);
        Set<Arista> fuera = new HashSet<>(rechazadas);

        List<Map<String, Object>> aristas = new ArrayList<>(modelo.aristas().size());
        for (Arista a : modelo.aristas()) {
            String estado = enArbol.contains(a) ? RedUtils.ESTADO_SOLUCION
                    : fuera.contains(a) ? RedUtils.ESTADO_DESCARTADA
                    : RedUtils.ESTADO_NORMAL;
            aristas.add(RedUtils.arista(a, estado));
        }

        Map<String, Object> datos = RedUtils.datosBase(
                modelo.metodo(), modelo.nodos(), aristas, null, null);
        if (evaluada != null)
            datos.put("aristaEvaluada", RedUtils.claveArco(evaluada.origen(), evaluada.destino()));
        datos.put("pesoAcumulado", RedUtils.round(pesoAcumulado));
        return datos;
    }
}
