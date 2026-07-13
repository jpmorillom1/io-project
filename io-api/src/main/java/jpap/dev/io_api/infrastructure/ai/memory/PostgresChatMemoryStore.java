package jpap.dev.io_api.infrastructure.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jpap.dev.io_api.infrastructure.persistence.entity.ChatMemoryEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.ChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Respaldo en PostgreSQL de la memoria conversacional. Sustituye al almacén en RAM
 * que traía MessageWindowChatMemory.withMaxMessages(...): una sesión sobrevive ahora
 * a un reinicio del backend.
 *
 * El memoryId es el sesionId, compartido por los seis subagentes, de modo que hay
 * UNA sola ventana por sesión y el hilo conversacional no se pierde cuando el
 * supervisor enruta a otro módulo.
 *
 * El contrato de ChatMemoryStore es de reemplazo total: updateMessages recibe la
 * ventana completa ya recortada, así que una fila por sesión basta.
 */
@Slf4j
@Component
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryRepository repository;

    public PostgresChatMemoryStore(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        return repository.findById(sesionId(memoryId))
                .<List<ChatMessage>>map(entity ->
                        new ArrayList<>(ChatMessageDeserializer.messagesFromJson(entity.getMensajes())))
                .orElseGet(ArrayList::new);
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        UUID sesionId = sesionId(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);

        ChatMemoryEntity entity = repository.findById(sesionId)
                .orElseGet(() -> new ChatMemoryEntity(sesionId, json));
        entity.setMensajes(json);
        entity.setActualizado(LocalDateTime.now());

        repository.save(entity);
        log.debug("[MEMORIA] sesion={} mensajes={}", sesionId, messages.size());
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        repository.deleteById(sesionId(memoryId));
    }

    /**
     * El memoryId llega como el String que el controlador generó con UUID.randomUUID().
     * La fila de `sesion` ya debe existir: chat_memory.sesion_id tiene FK contra ella.
     */
    private UUID sesionId(Object memoryId) {
        if (memoryId instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(memoryId));
    }
}
