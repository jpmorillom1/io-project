package jpap.dev.io_api.infrastructure.ai.actividad;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;

/**
 * Nombre del algoritmo tal y como el estudiante lo estudió, para anunciarlo en la UI.
 *
 * No basta con {@link MetodoResolucion}: en cuatro de los nueve métodos el algoritmo
 * concreto viaja DENTRO del modelo (TRANSPORTE puede ser Vogel o MODI; REDES puede ser
 * Dijkstra o Kruskal; INVENTARIO y PROGRAMACION_DINAMICA tienen cinco submodelos cada uno).
 * Decir "Resolviendo con TRANSPORTE" no le enseña nada a nadie; "Resolviendo con MODI", sí.
 */
public final class EtiquetaMetodo {

    private EtiquetaMetodo() {}

    public static String de(MetodoResolucion metodo, ModeloResoluble modelo) {
        return switch (metodo) {
            case SIMPLEX -> "Simplex";
            case GRAN_M -> "Gran M";
            case DOS_FASES -> "Dos Fases";
            case GRAFICO -> "el método gráfico";
            case BRANCH_AND_BOUND -> "Branch & Bound";
            case TRANSPORTE -> transporte(modelo);
            case REDES -> redes(modelo);
            case INVENTARIO -> inventario(modelo);
            case PROGRAMACION_DINAMICA -> dinamica(modelo);
        };
    }

    private static String transporte(ModeloResoluble modelo) {
        if (!(modelo instanceof ModeloTransporte m) || m.metodo() == null) return "Transporte";
        return switch (m.metodo()) {
            case ESQUINA_NOROESTE -> "Esquina Noroeste";
            case COSTO_MINIMO -> "Costo Mínimo";
            case VOGEL -> "Vogel";
            case MODI -> "MODI";
        };
    }

    private static String redes(ModeloResoluble modelo) {
        if (!(modelo instanceof ModeloRed m) || m.metodo() == null) return "Redes";
        return switch (m.metodo()) {
            case DIJKSTRA -> "Dijkstra";
            case KRUSKAL -> "Kruskal";
            case EDMONDS_KARP -> "Edmonds-Karp";
            case FLUJO_COSTO_MINIMO -> "Flujo de Costo Mínimo";
            case ASIGNACION -> "Asignación";
        };
    }

    private static String inventario(ModeloResoluble modelo) {
        if (!(modelo instanceof ModeloInventario m) || m.metodo() == null) return "Inventarios";
        return switch (m.metodo()) {
            case EOQ_BASICO -> "EOQ básico";
            case EOQ_DESCUENTOS -> "EOQ con descuentos";
            case EOQ_FALTANTES -> "EOQ con faltantes";
            case PRODUCCION_ECONOMICA -> "Producción Económica";
            case PUNTO_REORDEN -> "Punto de Reorden";
        };
    }

    private static String dinamica(ModeloResoluble modelo) {
        if (!(modelo instanceof ModeloDinamico m) || m.metodo() == null) return "Programación Dinámica";
        return switch (m.metodo()) {
            case ASIGNACION_RECURSOS -> "Asignación de Recursos";
            case MOCHILA -> "Mochila";
            case RUTA_ETAPAS -> "Ruta por Etapas";
            case PLANIFICACION_PRODUCCION -> "Planificación de Producción";
            case REEMPLAZO_EQUIPOS -> "Reemplazo de Equipos";
        };
    }
}
