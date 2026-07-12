package jpap.dev.io_api.infrastructure.ai.actividad;

/**
 * Fases por las que pasa un turno del chat, en el orden en que suelen ocurrir.
 *
 * El texto que ve el estudiante se compone AQUÍ y viaja ya formado hasta la UI:
 * el frontend solo lo pinta. Así el vocabulario ("Resolviendo con MODI") vive en
 * un único sitio y no hay que mantener un diccionario paralelo en TypeScript.
 *
 * No todas las fases ocurren en todos los turnos: una conversación puramente
 * socrática se queda en PENSANDO y ENRUTANDO.
 */
public enum FaseActividad {

    /** El turno acaba de entrar; aún no sabemos de qué va. */
    PENSANDO("Analizando el enunciado"),

    /** El supervisor ya eligió el subagente. El detalle es el módulo. */
    ENRUTANDO("Consultando el módulo de %s"),

    /** El tutor invocó registrarModeloSugerido. */
    FORMULANDO("Formulando el modelo"),

    /** El tutor invocó registrarValidacion. */
    VALIDANDO("Validando tu modelo"),

    /** Una tool de resolución creó la solicitud HITL. El detalle es el método. */
    PREPARANDO("Preparando la resolución por %s"),

    /** El humano aprobó y el solver está corriendo. El detalle es el método. */
    RESOLVIENDO("Resolviendo con %s"),

    /** El solver terminó; el tutor está redactando la explicación. */
    EXPLICANDO("Preparando la explicación");

    private final String plantilla;

    FaseActividad(String plantilla) {
        this.plantilla = plantilla;
    }

    /**
     * Texto listo para mostrar. Las fases con hueco (`%s`) que se publiquen sin
     * detalle caerían en un "Resolviendo con null": en ese caso se recorta la
     * plantilla y se devuelve solo su parte fija.
     */
    public String texto(String detalle) {
        if (!plantilla.contains("%s")) return plantilla;
        if (detalle == null || detalle.isBlank()) {
            return plantilla.substring(0, plantilla.indexOf("%s")).trim();
        }
        return plantilla.formatted(detalle);
    }
}
