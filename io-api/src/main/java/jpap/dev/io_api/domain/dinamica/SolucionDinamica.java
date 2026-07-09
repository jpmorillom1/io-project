package jpap.dev.io_api.domain.dinamica;

import java.util.List;

/**
 * Solución de un modelo de Programación Dinámica determinística. Es un record unificado
 * para los cinco submodelos; solo {@code rutaOptima} depende del método (null salvo en
 * RUTA_ETAPAS).
 *
 * Cubre uno a uno los elementos que un modelo de PD debe contener:
 *   etapas → definicionEtapas · estados → definicionEstados · decisiones → definicionDecisiones ·
 *   función de recurrencia → funcionRecurrencia (y su instancia por etapa en cada TablaEtapa) ·
 *   tabla de solución → tablas · principio de optimalidad → principioOptimalidad ·
 *   interpretación de la política óptima → politicaOptima + interpretacionPolitica
 *
 * @param valorOptimo            valor de la función de recurrencia en la etapa 1 y el estado inicial
 * @param tablas                 una por etapa, en el orden en que se calcularon (recursión hacia atrás)
 * @param politicaOptima         qué decisión tomar en cada etapa, recuperada hacia adelante
 * @param rutaOptima             secuencia de nodos del origen al destino (solo RUTA_ETAPAS)
 * @param definicionEtapas       qué representa una etapa en este problema
 * @param definicionEstados      qué representa un estado en este problema
 * @param definicionDecisiones   qué se decide en cada etapa
 * @param funcionRecurrencia     forma general de la recurrencia y su condición de frontera
 * @param principioOptimalidad   por qué la recursión es válida en este problema
 * @param interpretacionPolitica frase que resume, en lenguaje del problema, qué hacer
 */
public record SolucionDinamica(
        double valorOptimo,
        List<TablaEtapa> tablas,
        List<DecisionOptima> politicaOptima,
        List<String> rutaOptima,
        String definicionEtapas,
        String definicionEstados,
        String definicionDecisiones,
        String funcionRecurrencia,
        String principioOptimalidad,
        String interpretacionPolitica
) {

    public static Builder builder() {
        return new Builder();
    }

    /** Builder para no arrastrar 10 argumentos posicionales en cada solver. */
    public static final class Builder {
        private double valorOptimo;
        private List<TablaEtapa> tablas;
        private List<DecisionOptima> politicaOptima;
        private List<String> rutaOptima;
        private String definicionEtapas;
        private String definicionEstados;
        private String definicionDecisiones;
        private String funcionRecurrencia;
        private String principioOptimalidad;
        private String interpretacionPolitica;

        public Builder valorOptimo(double v) { this.valorOptimo = v; return this; }
        public Builder tablas(List<TablaEtapa> v) { this.tablas = v; return this; }
        public Builder politicaOptima(List<DecisionOptima> v) { this.politicaOptima = v; return this; }
        public Builder rutaOptima(List<String> v) { this.rutaOptima = v; return this; }
        public Builder definicionEtapas(String v) { this.definicionEtapas = v; return this; }
        public Builder definicionEstados(String v) { this.definicionEstados = v; return this; }
        public Builder definicionDecisiones(String v) { this.definicionDecisiones = v; return this; }
        public Builder funcionRecurrencia(String v) { this.funcionRecurrencia = v; return this; }
        public Builder principioOptimalidad(String v) { this.principioOptimalidad = v; return this; }
        public Builder interpretacionPolitica(String v) { this.interpretacionPolitica = v; return this; }

        public SolucionDinamica build() {
            return new SolucionDinamica(valorOptimo, tablas, politicaOptima, rutaOptima,
                    definicionEtapas, definicionEstados, definicionDecisiones, funcionRecurrencia,
                    principioOptimalidad, interpretacionPolitica);
        }
    }
}
