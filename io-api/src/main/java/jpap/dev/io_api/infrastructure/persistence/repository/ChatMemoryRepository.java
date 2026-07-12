package jpap.dev.io_api.infrastructure.persistence.repository;

import jpap.dev.io_api.infrastructure.persistence.entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, UUID> {
}
