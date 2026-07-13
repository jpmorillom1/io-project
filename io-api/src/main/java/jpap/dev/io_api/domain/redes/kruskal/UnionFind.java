package jpap.dev.io_api.domain.redes.kruskal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Estructura Union-Find (conjuntos disjuntos) con compresión de caminos y unión por rango.
 * Kruskal la usa para detectar si una arista formaría un ciclo: dos nodos con la misma
 * raíz ya están conectados por el árbol parcial.
 *
 * Java puro, sin dependencias de framework.
 */
public class UnionFind {

    private final Map<String, String> padre = new HashMap<>();
    private final Map<String, Integer> rango = new HashMap<>();

    public UnionFind(Collection<String> elementos) {
        for (String e : elementos) {
            padre.put(e, e);
            rango.put(e, 0);
        }
    }

    /** Raíz del conjunto al que pertenece x (con compresión de caminos). */
    public String encontrar(String x) {
        String p = padre.get(x);
        if (!p.equals(x)) {
            p = encontrar(p);
            padre.put(x, p);
        }
        return p;
    }

    /** Une los conjuntos de a y b. Devuelve false si ya estaban conectados (habría ciclo). */
    public boolean unir(String a, String b) {
        String ra = encontrar(a);
        String rb = encontrar(b);
        if (ra.equals(rb)) return false;

        int comparacion = Integer.compare(rango.get(ra), rango.get(rb));
        if (comparacion < 0) {
            padre.put(ra, rb);
        } else if (comparacion > 0) {
            padre.put(rb, ra);
        } else {
            padre.put(rb, ra);
            rango.put(ra, rango.get(ra) + 1);
        }
        return true;
    }

    /** true si a y b ya pertenecen al mismo conjunto. */
    public boolean conectados(String a, String b) {
        return encontrar(a).equals(encontrar(b));
    }
}
