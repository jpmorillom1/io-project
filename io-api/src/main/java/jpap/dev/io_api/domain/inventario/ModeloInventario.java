package jpap.dev.io_api.domain.inventario;

import jpap.dev.io_api.domain.common.ModeloResoluble;

import java.util.List;

/**
 * Datos de entrada de un modelo determinista de inventario. Los campos se usan
 * según el {@link MetodoInventario}; los que no apliquen a un método van null
 * (mismo estilo que {@code ModeloRed}).
 *
 *   - EOQ_BASICO:           demanda (D), costoOrden (K), costoMantener (H)
 *   - PRODUCCION_ECONOMICA: además tasaProduccion (P), con P &gt; D
 *   - EOQ_FALTANTES:        además costoFaltante (b) por unidad y periodo
 *   - PUNTO_REORDEN:        además leadTimeDias (L) y diasHabiles (default 360 si null)
 *   - EOQ_DESCUENTOS:       demanda (D), costoOrden (K), tramos (tabla de precios) y
 *                           tasaMantenerPorcentaje (i, fracción del precio) o costoMantener (H fijo)
 *
 * Todos los costos y cantidades se interpretan en base ANUAL salvo el punto de reorden,
 * que convierte a demanda diaria con {@code diasHabiles}.
 */
public record ModeloInventario(
        MetodoInventario metodo,
        Double demanda,
        Double costoOrden,
        Double costoMantener,
        Double costoFaltante,
        Double tasaProduccion,
        Double leadTimeDias,
        Integer diasHabiles,
        Double tasaMantenerPorcentaje,
        List<TramoDescuento> tramos
) implements ModeloResoluble {

    /** Días hábiles al año por defecto cuando el modelo no lo especifica. */
    public static final int DIAS_HABILES_DEFAULT = 360;

    /** Devuelve una copia con el método indicado (los controllers fuerzan el método por endpoint). */
    public ModeloInventario conMetodo(MetodoInventario nuevoMetodo) {
        return new ModeloInventario(nuevoMetodo, demanda, costoOrden, costoMantener, costoFaltante,
                tasaProduccion, leadTimeDias, diasHabiles, tasaMantenerPorcentaje, tramos);
    }

    /** Días hábiles efectivos: el valor dado o {@link #DIAS_HABILES_DEFAULT}. */
    public int diasHabilesOrDefault() {
        return diasHabiles != null ? diasHabiles : DIAS_HABILES_DEFAULT;
    }
}
