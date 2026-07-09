package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;

/**
 * Método de resolución que el tutor propone ejecutar.
 * La solicitud de aprobación humana lleva este método para que el estudiante
 * sepa exactamente qué algoritmo se ejecutará si aprueba.
 */
public enum MetodoResolucion {
    SIMPLEX,
    GRAN_M,
    DOS_FASES,
    GRAFICO,
    TRANSPORTE,
    REDES,
    BRANCH_AND_BOUND,
    INVENTARIO,
    PROGRAMACION_DINAMICA;

    public static Class<? extends ModeloResoluble> claseModeloPorMetodo(MetodoResolucion metodo) {
        return switch (metodo) {
            case SIMPLEX, GRAN_M, DOS_FASES, GRAFICO -> ModeloLP.class;
            case TRANSPORTE -> ModeloTransporte.class;
            case REDES -> ModeloRed.class;
            case BRANCH_AND_BOUND -> ModeloEntero.class;
            case INVENTARIO -> ModeloInventario.class;
            case PROGRAMACION_DINAMICA -> ModeloDinamico.class;
        };
    }
}
