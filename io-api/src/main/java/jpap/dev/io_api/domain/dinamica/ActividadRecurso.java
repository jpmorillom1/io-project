package jpap.dev.io_api.domain.dinamica;

import java.util.List;

/**
 * Una actividad (o periodo, o rubro de presupuesto) que compite por un recurso común
 * en el modelo ASIGNACION_RECURSOS. Es una etapa de la recursión.
 *
 * @param nombre   etiqueta legible de la actividad
 * @param retornos retornos[x] = beneficio (o costo) de asignarle exactamente x unidades del
 *                 recurso. Debe tener tamaño recursoTotal + 1, es decir cubrir x = 0..recursoTotal
 */
public record ActividadRecurso(
        String nombre,
        List<Double> retornos
) {}
