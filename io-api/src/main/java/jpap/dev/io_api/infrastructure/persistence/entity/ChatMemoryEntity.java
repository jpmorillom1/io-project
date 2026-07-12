package jpap.dev.io_api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ventana de mensajes de una sesión, tal como la mantiene MessageWindowChatMemory.
 * El JSON lo produce y consume ChatMessageSerializer/ChatMessageDeserializer —
 * no se interpreta aquí, ni se define un DTO de mensaje propio.
 */
@Entity
@Table(name = "chat_memory")
@Getter
@Setter
@NoArgsConstructor
public class ChatMemoryEntity {

    @Id
    @Column(name = "sesion_id")
    private UUID sesionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mensajes", nullable = false, columnDefinition = "jsonb")
    private String mensajes;

    @Column(name = "actualizado", nullable = false)
    private LocalDateTime actualizado;

    public ChatMemoryEntity(UUID sesionId, String mensajes) {
        this.sesionId = sesionId;
        this.mensajes = mensajes;
        this.actualizado = LocalDateTime.now();
    }
}
