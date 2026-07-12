package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.infrastructure.ai.dto.HistorialSesion;
import jpap.dev.io_api.infrastructure.ai.dto.MensajeHistorial;
import jpap.dev.io_api.infrastructure.ai.dto.ProblemaResueltoHistorial;
import jpap.dev.io_api.infrastructure.ai.dto.ResumenSesion;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import jpap.dev.io_api.infrastructure.ai.hitl.ResolucionEjecutor;
import jpap.dev.io_api.infrastructure.persistence.entity.InteraccionIaEntity;
import jpap.dev.io_api.infrastructure.persistence.entity.ProblemaResueltoEntity;
import jpap.dev.io_api.infrastructure.persistence.entity.SesionEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.InteraccionIaRepository;
import jpap.dev.io_api.infrastructure.persistence.repository.ProblemaResueltoRepository;
import jpap.dev.io_api.infrastructure.persistence.repository.SesionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de la sesión y su historial: sesion, interaccion_ia y problema_resuelto.
 *
 * La memoria que consume el LLM vive aparte, en chat_memory (PostgresChatMemoryStore).
 * Aquí se guarda el rastro legible: el transcript que rehidrata la UI y la evidencia
 * de cada resolución aprobada.
 *
 * Regla: registrar el historial NUNCA puede tumbar un turno del chat. Si la escritura
 * falla, se loguea y la respuesta del tutor sale igual.
 */
@Slf4j
@Service
public class ChatHistorialService {

    private final SesionRepository sesionRepository;
    private final InteraccionIaRepository interaccionRepository;
    private final ProblemaResueltoRepository problemaRepository;
    // Jackson 3 (tools.jackson), el mismo que serializa las respuestas HTTP en Spring Boot 4:
    // los JsonNode que salen de aquí viajan en el cuerpo de /historial.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final int retencionDias;

    public ChatHistorialService(SesionRepository sesionRepository,
                                InteraccionIaRepository interaccionRepository,
                                ProblemaResueltoRepository problemaRepository,
                                @Value("${app.sesion.retencion-dias:30}") int retencionDias) {
        this.sesionRepository = sesionRepository;
        this.interaccionRepository = interaccionRepository;
        this.problemaRepository = problemaRepository;
        this.retencionDias = retencionDias;
    }

    /**
     * Crea la sesión si no existe y refresca su marca de actividad.
     *
     * Debe llamarse ANTES de invocar al agente: chat_memory.sesion_id tiene una FK
     * contra sesion(id), y el primer turno escribe memoria.
     *
     * @return true si la sesión se acaba de crear — el llamador debe entonces titularla.
     */
    @Transactional
    public boolean asegurarSesion(String sesionId, String primerMensaje) {
        UUID id = UUID.fromString(sesionId);
        Optional<SesionEntity> existente = sesionRepository.findById(id);

        SesionEntity sesion = existente.orElseGet(() -> {
            SesionEntity nueva = new SesionEntity(id, primerMensaje);
            // Título de arranque: el modelo pequeño lo reemplaza en background, pero la
            // barra lateral nunca debe mostrar una conversación sin nombre.
            nueva.setTitulo(TituloSesionService.provisional(primerMensaje));
            return nueva;
        });
        sesion.setActualizada(LocalDateTime.now());
        sesionRepository.save(sesion);

        return existente.isEmpty();
    }

    /** Conversaciones para la barra lateral, la más reciente primero. */
    @Transactional(readOnly = true)
    public List<ResumenSesion> listarSesiones() {
        return sesionRepository.findAllByOrderByActualizadaDesc().stream()
                .map(s -> new ResumenSesion(
                        s.getId().toString(), s.getTitulo(), s.getModuloActivo(), s.getActualizada()))
                .toList();
    }

    /**
     * Todo lo necesario para reabrir una conversación. Vacío si el sesionId no existe
     * (o ni siquiera es un UUID): el cliente debe descartarlo y empezar de cero.
     */
    @Transactional(readOnly = true)
    public Optional<HistorialSesion> historialCompleto(String sesionId) {
        UUID id;
        try {
            id = UUID.fromString(sesionId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return sesionRepository.findById(id).map(sesion -> new HistorialSesion(
                sesionId,
                sesion.getTitulo(),
                sesion.getModuloActivo(),
                historial(sesionId),
                problemaRepository.findFirstBySesionIdOrderByResueltoEnDesc(id)
                        .map(this::aProblemaHistorial)
                        .orElse(null)
        ));
    }

    /** Enunciado original de la sesión (el primer mensaje del estudiante). */
    @Transactional(readOnly = true)
    public Optional<String> enunciadoDe(String sesionId) {
        return sesionRepository.findById(UUID.fromString(sesionId))
                .map(SesionEntity::getEnunciado);
    }

    /** Un turno de conversación. `toolLlamada` es el MetodoResolucion en juego, si lo hubo. */
    @Transactional
    public void registrarTurno(String sesionId, String modulo, String prompt,
                               String respuesta, String toolLlamada) {
        interaccionRepository.save(new InteraccionIaEntity(
                UUID.fromString(sesionId), modulo, prompt, respuesta, toolLlamada));
    }

    /** Evidencia de una resolución aprobada: el modelo que entró y el SolveResult que salió. */
    @Transactional
    public void registrarProblemaResuelto(String sesionId, MetodoResolucion metodo,
                                          ModeloResoluble modelo, ResolucionEjecutor.Ejecucion ejecucion) {
        UUID id = UUID.fromString(sesionId);
        String enunciado = enunciadoDe(sesionId).orElse(null);

        problemaRepository.save(new ProblemaResueltoEntity(
                id,
                metodo.name(),
                enunciado,
                aJson(modelo),
                aJson(resultadoDe(ejecucion))
        ));
    }

    /**
     * Transcript de la sesión en orden cronológico.
     *
     * Los turnos cuyo prompt es un mensaje `[SISTEMA]` (la reanudación tras una decisión
     * HITL) aportan solo la respuesta del tutor: el mensaje de sistema es plomería interna
     * y el estudiante nunca lo escribió.
     */
    @Transactional(readOnly = true)
    public List<MensajeHistorial> historial(String sesionId) {
        List<MensajeHistorial> mensajes = new ArrayList<>();
        for (InteraccionIaEntity turno : interaccionRepository.findBySesionIdOrderByFechaAsc(UUID.fromString(sesionId))) {
            if (!esMensajeDeSistema(turno.getPrompt())) {
                mensajes.add(new MensajeHistorial("user", turno.getPrompt(), turno.getFecha()));
            }
            if (turno.getRespuesta() != null && !turno.getRespuesta().isBlank()) {
                mensajes.add(new MensajeHistorial("tutor", turno.getRespuesta(), turno.getFecha()));
            }
        }
        return mensajes;
    }

    /** Retención: descarta sesiones sin actividad. Las filas hijas caen por las FK. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgarSesionesInactivas() {
        int borradas = sesionRepository.deleteByActualizadaBefore(
                LocalDateTime.now().minusDays(retencionDias));
        if (borradas > 0) {
            log.info("[HISTORIAL] {} sesiones purgadas por inactividad (> {} días)", borradas, retencionDias);
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private boolean esMensajeDeSistema(String prompt) {
        return prompt != null && prompt.stripLeading().startsWith("[SISTEMA]");
    }

    /** Solo uno de los siete resultados de una Ejecucion es non-null. */
    private Object resultadoDe(ResolucionEjecutor.Ejecucion e) {
        if (e == null) return null;
        if (e.resultado() != null) return e.resultado();
        if (e.resultadoGrafico() != null) return e.resultadoGrafico();
        if (e.resultadoTransporte() != null) return e.resultadoTransporte();
        if (e.resultadoRed() != null) return e.resultadoRed();
        if (e.resultadoEntero() != null) return e.resultadoEntero();
        if (e.resultadoInventario() != null) return e.resultadoInventario();
        return e.resultadoDinamica();
    }

    private String aJson(Object valor) {
        if (valor == null) return null;
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            log.warn("[HISTORIAL] no se pudo serializar {}: {}", valor.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private ProblemaResueltoHistorial aProblemaHistorial(ProblemaResueltoEntity p) {
        return new ProblemaResueltoHistorial(
                p.getModulo(), aJsonNode(p.getModeloJson()), aJsonNode(p.getResultado()));
    }

    /** Las columnas JSONB llegan como String; devolverlas tal cual daría un JSON escapado. */
    private JsonNode aJsonNode(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[HISTORIAL] JSON ilegible en problema_resuelto: {}", e.getMessage());
            return null;
        }
    }
}
