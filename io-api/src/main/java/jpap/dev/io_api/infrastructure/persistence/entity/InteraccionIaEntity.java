package jpap.dev.io_api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un turno de conversación: lo que preguntó el estudiante y lo que respondió el tutor.
 * Es la fuente del historial que rehidrata la UI y el registro de uso de IA.
 */
@Entity
@Table(name = "interaccion_ia")
@Getter
@Setter
@NoArgsConstructor
public class InteraccionIaEntity {

    @Id
    private UUID id;

    @Column(name = "sesion_id")
    private UUID sesionId;

    /** chat | extract | analyze */
    @Column(name = "herramienta", length = 60)
    private String herramienta;

    /** Módulo de IO que atendió el turno. */
    @Column(name = "objetivo")
    private String objetivo;

    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Column(name = "respuesta")
    private String respuesta;

    /** MetodoResolucion solicitado o ejecutado en el turno, si lo hubo. */
    @Column(name = "tool_llamada", length = 100)
    private String toolLlamada;

    @Column(name = "fragmentos_rag")
    private String fragmentosRag;

    @Column(name = "analisis_critico")
    private String analisisCritico;

    @Column(name = "correccion")
    private String correccion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    public InteraccionIaEntity(UUID sesionId, String objetivo, String prompt,
                               String respuesta, String toolLlamada) {
        this.id = UUID.randomUUID();
        this.sesionId = sesionId;
        this.herramienta = "chat";
        this.objetivo = objetivo;
        this.prompt = prompt;
        this.respuesta = respuesta;
        this.toolLlamada = toolLlamada;
        this.fecha = LocalDateTime.now();
    }
}
