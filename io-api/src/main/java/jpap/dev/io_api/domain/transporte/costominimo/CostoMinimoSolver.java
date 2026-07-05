package jpap.dev.io_api.domain.transporte.costominimo;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.transporte.Balanceador;
import jpap.dev.io_api.domain.transporte.Balanceador.Balanceado;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import jpap.dev.io_api.domain.transporte.TransporteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Método del Costo Mínimo: construye una solución básica inicial eligiendo en cada
 * paso la celda de menor costo unitario disponible y asignándole el máximo posible.
 * Suele dar un mejor punto de partida que la esquina noroeste porque sí mira los costos.
 *
 * Java puro, sin dependencias de framework.
 */
public class CostoMinimoSolver {

    private static final double EPS = 1e-9;

    public SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo) {
        Balanceado b = Balanceador.balancear(modelo);
        int fil = b.oferta().length;
        int col = b.demanda().length;

        double[][] x = new double[fil][col];
        boolean[][] basica = new boolean[fil][col];

        List<SolveStep> pasos = new ArrayList<>();
        Map<String, Object> datos0 = TransporteUtils.datosBase(b, x, basica);
        pasos.add(new SolveStep(0, "Balanceo y tabla inicial (Costo Mínimo)",
                TransporteUtils.descripcionBalanceo(b), datos0));

        asignar(b, x, basica, pasos);

        Map<String, Object> datosF = TransporteUtils.datosBase(b, x, basica);
        datosF.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        datosF.put("celdasBasicas", TransporteUtils.celdasBasicas(basica));
        pasos.add(new SolveStep(pasos.size(), "Solución básica inicial completa",
                "Todas las ofertas y demandas quedaron cubiertas. Costo de esta solución inicial = "
                        + TransporteUtils.costoTotal(b.costos(), x)
                        + ". MODI puede verificar si es óptima o mejorarla.",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                TransporteUtils.solucion(b, x, null, null), pasos);
    }

    static void asignar(Balanceado b, double[][] x, boolean[][] basica, List<SolveStep> pasos) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        double[] of = b.oferta().clone();
        double[] de = b.demanda().clone();
        boolean[] filaLista = new boolean[fil];
        boolean[] colLista = new boolean[col];

        while (true) {
            int mejorI = -1, mejorJ = -1;
            double mejorCosto = Double.MAX_VALUE;
            for (int i = 0; i < fil; i++) {
                if (filaLista[i]) continue;
                for (int j = 0; j < col; j++) {
                    if (colLista[j]) continue;
                    if (b.costos()[i][j] < mejorCosto - EPS) {
                        mejorCosto = b.costos()[i][j];
                        mejorI = i;
                        mejorJ = j;
                    }
                }
            }
            if (mejorI < 0) break; // no quedan celdas disponibles

            int i = mejorI, j = mejorJ;
            double cant = Math.min(of[i], de[j]);
            x[i][j] += cant;
            basica[i][j] = true;
            of[i] -= cant;
            de[j] -= cant;

            if (pasos != null) {
                Map<String, Object> datos = TransporteUtils.datosBase(b, x, basica);
                datos.put("celda", new int[]{i, j});
                datos.put("cantidad", TransporteUtils.round(cant));
                pasos.add(new SolveStep(pasos.size(),
                        "Celda de menor costo: (" + b.origenes().get(i) + " → " + b.destinos().get(j)
                                + ") con costo " + TransporteUtils.round(mejorCosto),
                        "Es la ruta más barata disponible. Se le asigna todo lo posible: min(oferta="
                                + TransporteUtils.round(of[i] + cant) + ", demanda="
                                + TransporteUtils.round(de[j] + cant) + ") = " + TransporteUtils.round(cant) + ".",
                        datos));
            }

            if (of[i] <= EPS && de[j] <= EPS) {
                if (contarDisponibles(filaLista) > 1) filaLista[i] = true; else colLista[j] = true;
            } else if (of[i] <= EPS) {
                filaLista[i] = true;
            } else {
                colLista[j] = true;
            }
        }
    }

    private static int contarDisponibles(boolean[] listas) {
        int c = 0;
        for (boolean l : listas) if (!l) c++;
        return c;
    }

    /** Matriz de asignación inicial (sin pasos), reutilizada por MODI. */
    public static double[][] construir(Balanceado b) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        double[][] x = new double[fil][col];
        boolean[][] basica = new boolean[fil][col];
        asignar(b, x, basica, null);
        return x;
    }
}
