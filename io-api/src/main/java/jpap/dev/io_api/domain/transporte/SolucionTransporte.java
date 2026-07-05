package jpap.dev.io_api.domain.transporte;

import java.util.List;

/**
 * Resultado de un problema de transporte.
 *
 * asignaciones es la matriz origenes×destinos con la cantidad enviada en cada ruta
 * (0 si no se usa la ruta). origenes/destinos incluyen el ficticio si hubo balanceo.
 *
 * comparativaInicial y metodoInicial solo se rellenan en MODI: la comparación de los
 * tres métodos iniciales y desde cuál se arrancó la optimización.
 */
public record SolucionTransporte(
        List<String> origenes,
        List<String> destinos,
        List<List<Double>> asignaciones,
        double costoTotal,
        List<CostoPorMetodo> comparativaInicial,
        MetodoTransporte metodoInicial
) {}
