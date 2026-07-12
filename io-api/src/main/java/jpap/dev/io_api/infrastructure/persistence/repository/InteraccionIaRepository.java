package jpap.dev.io_api.infrastructure.persistence.repository;

import jpap.dev.io_api.infrastructure.persistence.entity.InteraccionIaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteraccionIaRepository extends JpaRepository<InteraccionIaEntity, UUID> {

    List<InteraccionIaEntity> findBySesionIdOrderByFechaAsc(UUID sesionId);
}
