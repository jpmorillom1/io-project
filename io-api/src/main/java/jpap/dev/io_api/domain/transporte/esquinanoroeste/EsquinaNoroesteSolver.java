package jpap.dev.io_api.domain.transporte.esquinanoroeste;

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
 * Método de la Esquina Noroeste: construye una solución básica inicial recorriendo
 * la tabla desde la celda superior izquierda hacia la inferior derecha, asignando en
 * cada paso el mínimo entre la oferta y la demanda restantes. No mira los costos —
 * solo produce un punto de partida factible que luego MODI puede optimizar.
 *
 * Java puro, sin dependencias de framework.
 */
public class EsquinaNoroesteSolver {

    private static final double EPS = 1e-9;

    public SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo) {
        Balanceado b = Balanceador.balancear(modelo);
        int fil = b.oferta().length;
        int col = b.demanda().length;

        double[][] x = new double[fil][col];
        boolean[][] basica = new boolean[fil][col];

        List<SolveStep> pasos = new ArrayList<>();
        Map<String, Object> datos0 = TransporteUtils.datosBase(b, x, basica);
        pasos.add(new SolveStep(0, "Balanceo y tabla inicial (Esquina Noroeste)",
                TransporteUtils.descripcionBalanceo(b), datos0));

        asignar(b, x, basica, pasos);

        Map<String, Object> datosF = TransporteUtils.datosBase(b, x, basica);
        datosF.put("costoTotal", TransporteUtils.costoTotal(b.costos(), x));
        datosF.put("celdasBasicas", TransporteUtils.celdasBasicas(basica));
        pasos.add(new SolveStep(pasos.size(), "Solución básica inicial completa",
                "Se cubrieron todas las ofertas y demandas. Costo de esta solución inicial = "
                        + TransporteUtils.costoTotal(b.costos(), x)
                        + ". No es necesariamente óptima: MODI puede mejorarla.",
                datosF));

        return new SolveResult<>(SolveStatus.OPTIMO,
                TransporteUtils.solucion(b, x, null, null), pasos);
    }

    /** Rellena x/basica con la asignación noroeste. Si pasos != null, registra cada asignación. */
    static void asignar(Balanceado b, double[][] x, boolean[][] basica, List<SolveStep> pasos) {
        int fil = b.oferta().length;
        int col = b.demanda().length;
        double[] of = b.oferta().clone();
        double[] de = b.demanda().clone();

        int i = 0, j = 0;
        while (i < fil && j < col) {
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
                        "Asignar " + TransporteUtils.round(cant) + " a ("
                                + b.origenes().get(i) + " → " + b.destinos().get(j) + ")",
                        "Celda noroeste disponible. Se asigna min(oferta restante="
                                + TransporteUtils.round(of[i] + cant) + ", demanda restante="
                                + TransporteUtils.round(de[j] + cant) + ") = " + TransporteUtils.round(cant) + ".",
                        datos));
            }

            if (of[i] <= EPS && de[j] <= EPS) {
                if (j < col - 1) j++; else i++;
            } else if (of[i] <= EPS) {
                i++;
            } else {
                j++;
            }
        }
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
