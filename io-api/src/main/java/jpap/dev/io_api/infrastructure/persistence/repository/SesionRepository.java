package jpap.dev.io_api.infrastructure.persistence.repository;

import jpap.dev.io_api.infrastructure.persistence.entity.SesionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SesionRepository extends JpaRepository<SesionEntity, UUID> {

    /** Retención: las filas hijas se limpian por las FK (CASCADE en chat_memory, SET NULL en el resto). */
    int deleteByActualizadaBefore(LocalDateTime limite);

    /** Conversaciones para la barra lateral: la más reciente primero. */
    List<SesionEntity> findAllByOrderByActualizadaDesc();

    /**
     * UPDATE dirigido a la columna, no un save() de la entidad completa: el titulador corre
     * en background mientras el turno del chat escribe modulo_activo y actualizada sobre la
     * misma fila, y un save() con una copia rancia pisaría esos campos.
     */
    @Modifying
    @Transactional
    @Query("update SesionEntity s set s.titulo = :titulo where s.id = :id")
    void actualizarTitulo(@Param("id") UUID id, @Param("titulo") String titulo);
}
