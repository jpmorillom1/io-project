package jpap.dev.io_api.infrastructure.ai.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import jpap.dev.io_api.infrastructure.persistence.entity.ChatMemoryEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.ChatMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La memoria conversacional se serializa a JSONB. Si el round-trip pierde los tool calls,
 * el LLM recibe un AiMessage que dice "llamé a una herramienta" sin el resultado —
 * o peor, un ToolExecutionResultMessage huérfano que los proveedores rechazan.
 */
class PostgresChatMemoryStoreTest {

    private static final UUID SESION = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ChatMemoryRepository repository;
    private PostgresChatMemoryStore store;

    @BeforeEach
    void setUp() {
        repository = mock(ChatMemoryRepository.class);
        store = new PostgresChatMemoryStore(repository);
    }

    @Test
    @DisplayName("El round-trip conserva system, user, tool call y tool result en orden")
    void roundTripConservaToolCalls() {
        ToolExecutionRequest peticion = ToolExecutionRequest.builder()
                .id("call_1")
                .name("resolverSimplex")
                .arguments("{\"variables\":[\"x1\",\"x2\"]}")
                .build();

        List<ChatMessage> original = List.of(
                SystemMessage.from("Eres un tutor socrático."),
                UserMessage.from("Maximizar 5x1 + 4x2"),
                AiMessage.from(peticion),
                ToolExecutionResultMessage.from(peticion, "Z* = 20 en (4,0)"),
                AiMessage.from("¿Cómo interpretas ese resultado?")
        );

        // Escribe: capturamos el JSON que iría a la columna jsonb.
        store.updateMessages(SESION.toString(), original);

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(repository).save(captor.capture());
        String json = captor.getValue().getMensajes();

        // Lee: el store devuelve exactamente lo que se guardó.
        when(repository.findById(SESION)).thenReturn(Optional.of(new ChatMemoryEntity(SESION, json)));
        List<ChatMessage> recuperado = store.getMessages(SESION.toString());

        assertThat(recuperado).containsExactlyElementsOf(original);

        AiMessage conToolCall = (AiMessage) recuperado.get(2);
        assertThat(conToolCall.hasToolExecutionRequests()).isTrue();
        assertThat(conToolCall.toolExecutionRequests().getFirst().name()).isEqualTo("resolverSimplex");

        ToolExecutionResultMessage resultado = (ToolExecutionResultMessage) recuperado.get(3);
        assertThat(resultado.id()).isEqualTo("call_1");
        assertThat(resultado.text()).isEqualTo("Z* = 20 en (4,0)");
    }

    @Test
    @DisplayName("Una sesión sin memoria devuelve una lista mutable y vacía")
    void sesionSinMemoriaDevuelveListaMutable() {
        when(repository.findById(SESION)).thenReturn(Optional.empty());

        List<ChatMessage> mensajes = store.getMessages(SESION.toString());

        assertThat(mensajes).isEmpty();
        // MessageWindowChatMemory hace messages.add(...) sobre lo que devuelve el store.
        mensajes.add(UserMessage.from("hola"));
        assertThat(mensajes).hasSize(1);
    }

    @Test
    @DisplayName("Reescribir la ventana reutiliza la fila existente de la sesión")
    void updateReutilizaLaFilaExistente() {
        ChatMemoryEntity existente = new ChatMemoryEntity(SESION, "[]");
        when(repository.findById(SESION)).thenReturn(Optional.of(existente));

        store.updateMessages(SESION.toString(), List.of(UserMessage.from("hola")));

        verify(repository).save(existente);
        assertThat(existente.getMensajes()).contains("hola");
    }

    @Test
    @DisplayName("deleteMessages borra la memoria de la sesión")
    void deleteBorraLaMemoria() {
        store.deleteMessages(SESION.toString());
        verify(repository).deleteById(SESION);
    }

    @Test
    @DisplayName("Acepta el memoryId tanto como String como UUID")
    void aceptaMemoryIdComoUuid() {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThat(store.getMessages(SESION)).isEmpty();
        assertThat(store.getMessages(SESION.toString())).isEmpty();
    }
}
