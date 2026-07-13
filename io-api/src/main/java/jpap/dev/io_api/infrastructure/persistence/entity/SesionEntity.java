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
 * Sesión de trabajo del estudiante. Es la raíz: chat_memory cuelga de ella con
 * ON DELETE CASCADE, así que la fila debe existir antes del primer turno del chat.
 */
@Entity
@Table(name = "sesion")
@Getter
@Setter
@NoArgsConstructor
public class SesionEntity {

    @Id
    private UUID id;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "actualizada", nullable = false)
    private LocalDateTime actualizada;

    /** Nombre del ModuloIO que atiende la sesión; sustituye al mapa en RAM del supervisor. */
    @Column(name = "modulo_activo", length = 20)
    private String moduloActivo;

    /** Primer mensaje del estudiante, en lenguaje natural. */
    @Column(name = "enunciado")
    private String enunciado;

    /** Nombre que la barra lateral muestra. Lo redacta TituloSesionService. */
    @Column(name = "titulo", length = 120)
    private String titulo;

    public SesionEntity(UUID id, String enunciado) {
        LocalDateTime ahora = LocalDateTime.now();
        this.id = id;
        this.creadaEn = ahora;
        this.actualizada = ahora;
        this.enunciado = enunciado;
    }
}
