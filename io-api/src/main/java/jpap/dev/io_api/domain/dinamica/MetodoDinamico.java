package jpap.dev.io_api.domain.dinamica;

/**
 * Submodelos de Programación Dinámica determinística implementados.
 *
 * Todos comparten la misma mecánica (recursión hacia atrás sobre etapas y estados) y
 * por eso comparten {@link ModeloDinamico} y {@link SolucionDinamica}; lo que cambia es
 * qué es una etapa, qué es un estado y qué es una decisión.
 *
 * Aplicaciones que cubre cada uno:
 *   - ASIGNACION_RECURSOS:      asignación de recursos en varios periodos, distribución de presupuesto
 *   - MOCHILA:                  selección de inversiones o proyectos con consumo de un recurso
 *   - RUTA_ETAPAS:              problemas de rutas secuenciales (tipo diligencia)
 *   - PLANIFICACION_PRODUCCION: planificación de producción e inventarios por etapas
 *   - REEMPLAZO_EQUIPOS:        reemplazo de equipos a lo largo de un horizonte
 */
public enum MetodoDinamico {
    ASIGNACION_RECURSOS,
    MOCHILA,
    RUTA_ETAPAS,
    PLANIFICACION_PRODUCCION,
    REEMPLAZO_EQUIPOS
}
