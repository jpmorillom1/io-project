package jpap.dev.io_api.domain.transporte.modi;

import java.util.ArrayList;
import java.util.List;

/**
 * Encuentra el ciclo cerrado (stepping-stone) que parte de una celda no básica
 * entrante y regresa a ella pasando SOLO por celdas básicas, alternando movimientos
 * horizontales (misma fila) y verticales (misma columna).
 *
 * Con una base no degenerada de m+n−1 celdas independientes, ese ciclo existe y es único.
 * Devuelve las celdas en orden: índice 0 = celda entrante (signo +), luego −, +, −, ...
 */
final class CicloSteppingStone {

    private CicloSteppingStone() {}

    /**
     * @param entrada celda no básica entrante {i, j}
     * @param basicas celdas básicas actuales
     * @return ciclo ordenado empezando en la entrante, o null si no existe
     */
    static List<int[]> encontrar(int[] entrada, List<int[]> basicas) {
        // Se prueba arrancar por movimiento horizontal y, si falla, por vertical.
        for (boolean arranqueHorizontal : new boolean[]{true, false}) {
            List<int[]> camino = new ArrayList<>();
            camino.add(entrada);
            if (dfs(camino, arranqueHorizontal, basicas, entrada)) {
                return camino;
            }
        }
        return null;
    }

    private static boolean dfs(List<int[]> camino, boolean siguienteHorizontal,
                               List<int[]> nodos, int[] inicio) {
        int[] ultimo = camino.get(camino.size() - 1);

        // Cerrar el ciclo: al menos 4 celdas y el último comparte la línea del arranque con el inicio.
        if (camino.size() >= 4) {
            if (siguienteHorizontal && ultimo[0] == inicio[0]) return true;
            if (!siguienteHorizontal && ultimo[1] == inicio[1]) return true;
        }

        for (int[] c : nodos) {
            if (contiene(camino, c)) continue;
            boolean alineado = siguienteHorizontal ? (ultimo[0] == c[0]) : (ultimo[1] == c[1]);
            if (!alineado) continue;

            camino.add(c);
            if (dfs(camino, !siguienteHorizontal, nodos, inicio)) return true;
            camino.remove(camino.size() - 1);
        }
        return false;
    }

    private static boolean contiene(List<int[]> camino, int[] c) {
        for (int[] p : camino)
            if (p[0] == c[0] && p[1] == c[1]) return true;
        return false;
    }
}
