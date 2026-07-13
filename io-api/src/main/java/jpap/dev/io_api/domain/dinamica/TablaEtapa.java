package jpap.dev.io_api.domain.dinamica;

import java.util.List;

/**
 * Tabla de solución de una etapa de la recursión: una fila por estado alcanzable.
 * Es el artefacto que el estudiante rellena a mano y el que la UI muestra.
 *
 * @param etapa       número de etapa
 * @param nombreEtapa etiqueta legible ("Etapa 2 — Actividad B", "Año 3")
 * @param recurrencia la función de recurrencia instanciada para esta etapa
 * @param filas       una por estado alcanzable, en orden creciente de estado
 */
public record TablaEtapa(
        int etapa,
        String nombreEtapa,
        String recurrencia,
        List<FilaEtapa> filas
) {}
