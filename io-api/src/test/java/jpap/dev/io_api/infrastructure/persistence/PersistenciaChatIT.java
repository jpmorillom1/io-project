package jpap.dev.io_api.infrastructure.persistence;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatHistorialService;
import jpap.dev.io_api.infrastructure.ai.dto.MensajeHistorial;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import jpap.dev.io_api.infrastructure.ai.hitl.ResolucionEjecutor;
import jpap.dev.io_api.infrastructure.ai.memory.PostgresChatMemoryStore;
import jpap.dev.io_api.infrastructure.persistence.entity.ProblemaResueltoEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.ProblemaResueltoRepository;
import jpap.dev.io_api.infrastructure.persistence.repository.SesionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración real contra PostgreSQL: comprueba que la sesión, la memoria del LLM
 * (columna jsonb) y el historial legible sobreviven a la ida y vuelta a la base.
 *
 * Requiere Postgres en localhost:5432 — se excluye en CI con -PciSkipContextTests.
 */
@SpringBootTest(properties = "app.rag.reingestar=false")
class PersistenciaChatIT {

    @MockitoBean
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired private ChatHistorialService historialService;
    @Autowired private PostgresChatMemoryStore memoryStore;
    @Autowired private SesionRepository sesionRepository;
    @Autowired private ProblemaResueltoRepository problemaRepository;

    private final String sesionId = UUID.randomUUID().toString();

    @AfterEach
    void limpiar() {
        // chat_memory cae por CASCADE; interaccion_ia y problema_resuelto por SET NULL.
        sesionRepository.deleteById(UUID.fromString(sesionId));
    }

    @Test
    @DisplayName("La memoria del chat sobrevive al viaje a la columna jsonb, con tool calls incluidos")
    void memoriaPersisteConToolCalls() {
        historialService.asegurarSesion(sesionId, "Maximizar 5x1 + 4x2");

        ToolExecutionRequest peticion = ToolExecutionRequest.builder()
                .id("call_1").name("resolverSimplex").arguments("{}").build();
        List<ChatMessage> ventana = List.of(
                SystemMessage.from("Eres un tutor socrático."),
                UserMessage.from("Maximizar 5x1 + 4x2"),
                AiMessage.from(peticion),
                ToolExecutionResultMessage.from(peticion, "Z* = 20"),
                AiMessage.from("¿Qué recurso limita la solución?")
        );

        memoryStore.updateMessages(sesionId, ventana);

        assertThat(memoryStore.getMessages(sesionId)).containsExactlyElementsOf(ventana);
    }

    @Test
    @DisplayName("Sin fila de sesión no se puede escribir memoria: la FK lo impide")
    void memoriaExigeSesionExistente() {
        String huerfana = UUID.randomUUID().toString();
        assertThat(sesionRepository.existsById(UUID.fromString(huerfana))).isFalse();

        // Por eso el controlador llama a asegurarSesion ANTES de invocar al agente.
        assertThatThrownBy(() -> memoryStore.updateMessages(huerfana, List.of(UserMessage.from("hola"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El historial ordena los turnos y oculta el mensaje [SISTEMA] del estudiante")
    void historialOcultaMensajesDeSistema() {
        historialService.asegurarSesion(sesionId, "Maximizar 5x1 + 4x2");
        historialService.registrarTurno(sesionId, "PL", "Maximizar 5x1 + 4x2", "¿Qué representa x1?", null);
        historialService.registrarTurno(sesionId, "PL",
                "[SISTEMA] El estudiante APROBÓ el modelo...", "El óptimo es Z=20.", "SIMPLEX");

        List<MensajeHistorial> historial = historialService.historial(sesionId);

        assertThat(historial).extracting(MensajeHistorial::rol)
                .containsExactly("user", "tutor", "tutor");
        assertThat(historial).extracting(MensajeHistorial::texto)
                .containsExactly("Maximizar 5x1 + 4x2", "¿Qué representa x1?", "El óptimo es Z=20.");
        assertThat(historial).noneMatch(m -> m.texto().contains("[SISTEMA]"));
    }

    @Test
    @DisplayName("Una resolución aprobada deja evidencia: enunciado, modelo y resultado en jsonb")
    void problemaResueltoGuardaModeloYResultado() {
        historialService.asegurarSesion(sesionId, "Maximizar 5x1 + 4x2");

        ModeloLP modelo = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                List.of(new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24.0)));
        var ejecucion = new ResolucionEjecutor.Ejecucion(
                new SolveResult<>(SolveStatus.OPTIMO, null, List.of()),
                null, null, null, null, null, null, "Z* = 20");

        historialService.registrarProblemaResuelto(sesionId, MetodoResolucion.SIMPLEX, modelo, ejecucion);

        ProblemaResueltoEntity guardado = problemaRepository
                .findFirstBySesionIdOrderByResueltoEnDesc(UUID.fromString(sesionId))
                .orElseThrow();

        assertThat(guardado.getModulo()).isEqualTo("SIMPLEX");
        assertThat(guardado.getEnunciado()).isEqualTo("Maximizar 5x1 + 4x2");
        assertThat(guardado.getModeloJson()).contains("\"MAXIMIZAR\"").contains("x1");
        assertThat(guardado.getResultado()).contains("OPTIMO");
    }

    @Test
    @DisplayName("asegurarSesion es idempotente y conserva el enunciado original")
    void asegurarSesionEsIdempotente() {
        historialService.asegurarSesion(sesionId, "Enunciado original");
        historialService.asegurarSesion(sesionId, "Segundo mensaje, no es el enunciado");

        assertThat(historialService.enunciadoDe(sesionId)).contains("Enunciado original");
        assertThat(sesionRepository.count()).isPositive();
    }
}
