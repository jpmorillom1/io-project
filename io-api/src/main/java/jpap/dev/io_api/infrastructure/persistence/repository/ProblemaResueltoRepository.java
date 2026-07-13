package jpap.dev.io_api.infrastructure.persistence.repository;

import jpap.dev.io_api.infrastructure.persistence.entity.ProblemaResueltoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProblemaResueltoRepository extends JpaRepository<ProblemaResueltoEntity, UUID> {

    /** Último problema resuelto de la sesión: base para releer el resultado en turnos posteriores. */
    Optional<ProblemaResueltoEntity> findFirstBySesionIdOrderByResueltoEnDesc(UUID sesionId);
}
