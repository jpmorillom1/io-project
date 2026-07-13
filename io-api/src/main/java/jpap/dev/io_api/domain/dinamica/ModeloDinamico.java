package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.ModeloResoluble;

import java.util.List;

/**
 * Datos de entrada de un modelo de Programación Dinámica determinística. Los campos se usan
 * según el {@link MetodoDinamico}; los que no apliquen a un método van null (mismo estilo
 * que {@code ModeloInventario} y {@code ModeloRed}).
 *
 *   - ASIGNACION_RECURSOS:      recursoTotal, actividades
 *   - MOCHILA:                  capacidad, articulos
 *   - RUTA_ETAPAS:              etapasRuta, arcos
 *   - PLANIFICACION_PRODUCCION: demandas, costoPreparacion, costoUnitarioProduccion, costoMantener
 *                               y, opcionales, capacidadProduccion, capacidadAlmacen,
 *                               inventarioInicial, inventarioFinal
 *   - REEMPLAZO_EQUIPOS:        horizonteAnios, edadMaxima, costoCompra, tablaEdades
 *                               y, opcional, edadInicial
 *
 * {@code sentido} solo es configurable en ASIGNACION_RECURSOS y RUTA_ETAPAS; en los demás
 * el sentido lo fija la naturaleza del modelo (mochila maximiza, producción minimiza costo,
 * reemplazo maximiza ingreso neto) y el validador rechaza un sentido contrario.
 */
public record ModeloDinamico(
        MetodoDinamico metodo,
        SentidoOptimizacion sentido,

        // ASIGNACION_RECURSOS
        Integer recursoTotal,
        List<ActividadRecurso> actividades,

        // MOCHILA
        Integer capacidad,
        List<ArticuloMochila> articulos,

        // RUTA_ETAPAS
        List<EtapaRuta> etapasRuta,
        List<ArcoRuta> arcos,

        // PLANIFICACION_PRODUCCION
        List<Integer> demandas,
        Double costoPreparacion,
        Double costoUnitarioProduccion,
        Double costoMantener,
        Integer capacidadProduccion,
        Integer capacidadAlmacen,
        Integer inventarioInicial,
        Integer inventarioFinal,

        // REEMPLAZO_EQUIPOS
        Integer horizonteAnios,
        Integer edadInicial,
        Integer edadMaxima,
        Double costoCompra,
        List<DatosEdadEquipo> tablaEdades
) implements ModeloResoluble {

    /** Devuelve una copia con el método indicado (los controllers fuerzan el método por endpoint). */
    public ModeloDinamico conMetodo(MetodoDinamico nuevoMetodo) {
        return new ModeloDinamico(nuevoMetodo, sentido, recursoTotal, actividades, capacidad, articulos,
                etapasRuta, arcos, demandas, costoPreparacion, costoUnitarioProduccion, costoMantener,
                capacidadProduccion, capacidadAlmacen, inventarioInicial, inventarioFinal,
                horizonteAnios, edadInicial, edadMaxima, costoCompra, tablaEdades);
    }

    /** Sentido efectivo: el indicado o el natural del método. */
    public SentidoOptimizacion sentidoOrDefault() {
        if (sentido != null) return sentido;
        if (metodo == null) return SentidoOptimizacion.MAXIMIZAR;
        return switch (metodo) {
            case RUTA_ETAPAS, PLANIFICACION_PRODUCCION -> SentidoOptimizacion.MINIMIZAR;
            case ASIGNACION_RECURSOS, MOCHILA, REEMPLAZO_EQUIPOS -> SentidoOptimizacion.MAXIMIZAR;
        };
    }

    public int inventarioInicialOrDefault() {
        return inventarioInicial != null ? inventarioInicial : 0;
    }

    public int inventarioFinalOrDefault() {
        return inventarioFinal != null ? inventarioFinal : 0;
    }

    public int edadInicialOrDefault() {
        return edadInicial != null ? edadInicial : 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder para no arrastrar 21 argumentos posicionales (la mayoría null) en cada llamada. */
    public static final class Builder {
        private MetodoDinamico metodo;
        private SentidoOptimizacion sentido;
        private Integer recursoTotal;
        private List<ActividadRecurso> actividades;
        private Integer capacidad;
        private List<ArticuloMochila> articulos;
        private List<EtapaRuta> etapasRuta;
        private List<ArcoRuta> arcos;
        private List<Integer> demandas;
        private Double costoPreparacion;
        private Double costoUnitarioProduccion;
        private Double costoMantener;
        private Integer capacidadProduccion;
        private Integer capacidadAlmacen;
        private Integer inventarioInicial;
        private Integer inventarioFinal;
        private Integer horizonteAnios;
        private Integer edadInicial;
        private Integer edadMaxima;
        private Double costoCompra;
        private List<DatosEdadEquipo> tablaEdades;

        public Builder metodo(MetodoDinamico v) { this.metodo = v; return this; }
        public Builder sentido(SentidoOptimizacion v) { this.sentido = v; return this; }
        public Builder recursoTotal(Integer v) { this.recursoTotal = v; return this; }
        public Builder actividades(List<ActividadRecurso> v) { this.actividades = v; return this; }
        public Builder capacidad(Integer v) { this.capacidad = v; return this; }
        public Builder articulos(List<ArticuloMochila> v) { this.articulos = v; return this; }
        public Builder etapasRuta(List<EtapaRuta> v) { this.etapasRuta = v; return this; }
        public Builder arcos(List<ArcoRuta> v) { this.arcos = v; return this; }
        public Builder demandas(List<Integer> v) { this.demandas = v; return this; }
        public Builder costoPreparacion(Double v) { this.costoPreparacion = v; return this; }
        public Builder costoUnitarioProduccion(Double v) { this.costoUnitarioProduccion = v; return this; }
        public Builder costoMantener(Double v) { this.costoMantener = v; return this; }
        public Builder capacidadProduccion(Integer v) { this.capacidadProduccion = v; return this; }
        public Builder capacidadAlmacen(Integer v) { this.capacidadAlmacen = v; return this; }
        public Builder inventarioInicial(Integer v) { this.inventarioInicial = v; return this; }
        public Builder inventarioFinal(Integer v) { this.inventarioFinal = v; return this; }
        public Builder horizonteAnios(Integer v) { this.horizonteAnios = v; return this; }
        public Builder edadInicial(Integer v) { this.edadInicial = v; return this; }
        public Builder edadMaxima(Integer v) { this.edadMaxima = v; return this; }
        public Builder costoCompra(Double v) { this.costoCompra = v; return this; }
        public Builder tablaEdades(List<DatosEdadEquipo> v) { this.tablaEdades = v; return this; }

        public ModeloDinamico build() {
            return new ModeloDinamico(metodo, sentido, recursoTotal, actividades, capacidad, articulos,
                    etapasRuta, arcos, demandas, costoPreparacion, costoUnitarioProduccion, costoMantener,
                    capacidadProduccion, capacidadAlmacen, inventarioInicial, inventarioFinal,
                    horizonteAnios, edadInicial, edadMaxima, costoCompra, tablaEdades);
        }
    }
}
