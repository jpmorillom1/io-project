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
 * Instancia resuelta tras una aprobación humana: el modelo que entró y el SolveResult
 * que salió. Es lo que permitirá releer el último resultado en turnos posteriores
 * sin depender de la ventana de memoria (ver docs/SENSIBILIDAD_CHAT.md §6.1).
 */
@Entity
@Table(name = "problema_resuelto")
@Getter
@Setter
@NoArgsConstructor
public class ProblemaResueltoEntity {

    @Id
    private UUID id;

    @Column(name = "sesion_id")
    private UUID sesionId;

    /** MetodoResolucion aplicado (SIMPLEX, TRANSPORTE, …). */
    @Column(name = "modulo", nullable = false, length = 30)
    private String modulo;

    @Column(name = "enunciado")
    private String enunciado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modelo_json", columnDefinition = "jsonb")
    private String modeloJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado", columnDefinition = "jsonb")
    private String resultado;

    @Column(name = "resuelto_en", nullable = false)
    private LocalDateTime resueltoEn;

    public ProblemaResueltoEntity(UUID sesionId, String modulo, String enunciado,
                                  String modeloJson, String resultado) {
        this.id = UUID.randomUUID();
        this.sesionId = sesionId;
        this.modulo = modulo;
        this.enunciado = enunciado;
        this.modeloJson = modeloJson;
        this.resultado = resultado;
        this.resueltoEn = LocalDateTime.now();
    }
}
