package jpap.dev.io_api.domain.inventario;

import java.util.List;

/**
 * Solución de un modelo determinista de inventario. Es un record unificado: solo los
 * campos que aplican al {@link MetodoInventario} resuelto vienen poblados, el resto null
 * (mismo estilo que {@code SolucionRed}).
 *
 * @param cantidadOptima        Q* — cantidad económica de pedido (o de producción en POQ)
 * @param costoTotalAnual       costo total anual; en EOQ con descuentos INCLUYE el costo de compra
 * @param costoOrdenarAnual     componente anual de ordenar/preparar pedidos = (D/Q)·K
 * @param costoMantenerAnual    componente anual de mantener inventario
 * @param numeroPedidos         N = D/Q* (numero de pedidos al ano)
 * @param tiempoCicloDias       T, duracion de un ciclo de inventario en dias habiles
 * @param nivelMaximoInventario inventario maximo alcanzado: Imax en POQ, S en faltantes (null si no aplica)
 * @param faltanteMaximo        faltante/backorder maximo permitido = Q menos S (solo faltantes)
 * @param costoFaltanteAnual    componente anual del costo por faltantes (solo faltantes)
 * @param puntoReorden          R, nivel de inventario que dispara un nuevo pedido (solo punto de reorden)
 * @param demandaDiaria         d = demanda entre dias habiles (solo punto de reorden)
 * @param costoCompraAnual      D por C, costo anual de compra del articulo (solo descuentos)
 * @param precioUnitarioOptimo  precio unitario del tramo elegido (solo descuentos)
 * @param comparativa           costo total por tramo evaluado (solo descuentos)
 * @param interpretacionPolitica frase que resume la política de inventario recomendada
 */
public record SolucionInventario(
        double cantidadOptima,
        double costoTotalAnual,
        double costoOrdenarAnual,
        double costoMantenerAnual,
        Double numeroPedidos,
        Double tiempoCicloDias,
        Double nivelMaximoInventario,
        Double faltanteMaximo,
        Double costoFaltanteAnual,
        Double puntoReorden,
        Double demandaDiaria,
        Double costoCompraAnual,
        Double precioUnitarioOptimo,
        List<ComparativaTramo> comparativa,
        String interpretacionPolitica
) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder para no arrastrar 15 argumentos (la mayoría null) en cada solver.
     * Los campos comunes (Q*, costos, N, T) casi siempre se llenan; los específicos
     * de cada método solo cuando aplican.
     */
    public static final class Builder {
        private double cantidadOptima;
        private double costoTotalAnual;
        private double costoOrdenarAnual;
        private double costoMantenerAnual;
        private Double numeroPedidos;
        private Double tiempoCicloDias;
        private Double nivelMaximoInventario;
        private Double faltanteMaximo;
        private Double costoFaltanteAnual;
        private Double puntoReorden;
        private Double demandaDiaria;
        private Double costoCompraAnual;
        private Double precioUnitarioOptimo;
        private List<ComparativaTramo> comparativa;
        private String interpretacionPolitica;

        public Builder cantidadOptima(double v) { this.cantidadOptima = v; return this; }
        public Builder costoTotalAnual(double v) { this.costoTotalAnual = v; return this; }
        public Builder costoOrdenarAnual(double v) { this.costoOrdenarAnual = v; return this; }
        public Builder costoMantenerAnual(double v) { this.costoMantenerAnual = v; return this; }
        public Builder numeroPedidos(Double v) { this.numeroPedidos = v; return this; }
        public Builder tiempoCicloDias(Double v) { this.tiempoCicloDias = v; return this; }
        public Builder nivelMaximoInventario(Double v) { this.nivelMaximoInventario = v; return this; }
        public Builder faltanteMaximo(Double v) { this.faltanteMaximo = v; return this; }
        public Builder costoFaltanteAnual(Double v) { this.costoFaltanteAnual = v; return this; }
        public Builder puntoReorden(Double v) { this.puntoReorden = v; return this; }
        public Builder demandaDiaria(Double v) { this.demandaDiaria = v; return this; }
        public Builder costoCompraAnual(Double v) { this.costoCompraAnual = v; return this; }
        public Builder precioUnitarioOptimo(Double v) { this.precioUnitarioOptimo = v; return this; }
        public Builder comparativa(List<ComparativaTramo> v) { this.comparativa = v; return this; }
        public Builder interpretacionPolitica(String v) { this.interpretacionPolitica = v; return this; }

        public SolucionInventario build() {
            return new SolucionInventario(cantidadOptima, costoTotalAnual, costoOrdenarAnual,
                    costoMantenerAnual, numeroPedidos, tiempoCicloDias, nivelMaximoInventario,
                    faltanteMaximo, costoFaltanteAnual, puntoReorden, demandaDiaria, costoCompraAnual,
                    precioUnitarioOptimo, comparativa, interpretacionPolitica);
        }
    }
}
