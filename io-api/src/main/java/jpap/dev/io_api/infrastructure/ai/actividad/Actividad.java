package jpap.dev.io_api.infrastructure.ai.actividad;

/**
 * Lo que Pivot está haciendo AHORA MISMO en una sesión.
 *
 * `secuencia` es un contador monótono por sesión: la UI lo usa para saber si la
 * fase cambió sin tener que comparar cadenas, y para descartar respuestas de
 * sondeos que lleguen fuera de orden.
 */
public record Actividad(
        FaseActividad fase,
        String texto,
        long secuencia
) {}
