package jpap.dev.io_api.domain.transporte;

import java.util.ArrayList;
import java.util.List;

/**
 * Balancea un problema de transporte antes de resolverlo.
 *
 * Si Σoferta ≠ Σdemanda no existe una solución que agote ambos lados, así que se
 * agrega un origen o destino FICTICIO con costo 0 que absorbe la diferencia:
 *   - Σoferta &lt; Σdemanda → origen ficticio con oferta = déficit (demanda no atendida).
 *   - Σoferta &gt; Σdemanda → destino ficticio con demanda = excedente (oferta sobrante).
 *
 * También valida la coherencia dimensional del modelo (lanza IllegalArgumentException).
 * Java puro, sin dependencias de framework.
 */
public final class Balanceador {

    private static final double EPSILON = 1e-9;

    private Balanceador() {}

    /** Modelo ya balanceado, en arreglos primitivos listos para los solvers. */
    public record Balanceado(
            List<String> origenes,
            List<String> destinos,
            double[] oferta,
            double[] demanda,
            double[][] costos,
            boolean origenFicticio,
            boolean destinoFicticio
    ) {}

    public static Balanceado balancear(ModeloTransporte modelo) {
        validar(modelo);

        int m = modelo.origenes().size();
        int n = modelo.destinos().size();

        List<String> origenes = new ArrayList<>(modelo.origenes());
        List<String> destinos = new ArrayList<>(modelo.destinos());

        double totalOferta = modelo.oferta().stream().mapToDouble(Double::doubleValue).sum();
        double totalDemanda = modelo.demanda().stream().mapToDouble(Double::doubleValue).sum();

        boolean origenFicticio = false;
        boolean destinoFicticio = false;

        // Filas extra (origen ficticio) o columnas extra (destino ficticio)
        int filas = m;
        int columnas = n;
        if (totalOferta < totalDemanda - EPSILON) { filas = m + 1; origenFicticio = true; }
        else if (totalOferta > totalDemanda + EPSILON) { columnas = n + 1; destinoFicticio = true; }

        double[] oferta = new double[filas];
        double[] demanda = new double[columnas];
        double[][] costos = new double[filas][columnas];

        for (int i = 0; i < m; i++) oferta[i] = modelo.oferta().get(i);
        for (int j = 0; j < n; j++) demanda[j] = modelo.demanda().get(j);
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                costos[i][j] = modelo.costos().get(i).get(j);

        if (origenFicticio) {
            oferta[m] = totalDemanda - totalOferta;   // costos[m][*] = 0 por defecto
            origenes.add("Ficticio");
        } else if (destinoFicticio) {
            demanda[n] = totalOferta - totalDemanda;   // costos[*][n] = 0 por defecto
            destinos.add("Ficticio");
        }

        return new Balanceado(origenes, destinos, oferta, demanda, costos, origenFicticio, destinoFicticio);
    }

    private static void validar(ModeloTransporte modelo) {
        if (modelo.origenes() == null || modelo.origenes().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos un origen.");
        if (modelo.destinos() == null || modelo.destinos().isEmpty())
            throw new IllegalArgumentException("El modelo necesita al menos un destino.");
        int m = modelo.origenes().size();
        int n = modelo.destinos().size();

        if (modelo.oferta() == null || modelo.oferta().size() != m)
            throw new IllegalArgumentException("El vector de oferta debe tener " + m + " valor(es), uno por origen.");
        if (modelo.demanda() == null || modelo.demanda().size() != n)
            throw new IllegalArgumentException("El vector de demanda debe tener " + n + " valor(es), uno por destino.");
        if (modelo.costos() == null || modelo.costos().size() != m)
            throw new IllegalArgumentException("La matriz de costos debe tener " + m + " fila(s), una por origen.");
        for (int i = 0; i < m; i++) {
            List<Double> fila = modelo.costos().get(i);
            if (fila == null || fila.size() != n)
                throw new IllegalArgumentException(
                        "La fila " + (i + 1) + " de la matriz de costos debe tener " + n + " columna(s), una por destino.");
        }
        for (int i = 0; i < m; i++)
            if (modelo.oferta().get(i) < -EPSILON)
                throw new IllegalArgumentException("La oferta del origen " + (i + 1) + " no puede ser negativa.");
        for (int j = 0; j < n; j++)
            if (modelo.demanda().get(j) < -EPSILON)
                throw new IllegalArgumentException("La demanda del destino " + (j + 1) + " no puede ser negativa.");
    }
}
