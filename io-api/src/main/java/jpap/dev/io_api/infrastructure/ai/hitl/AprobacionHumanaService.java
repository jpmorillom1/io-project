package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.infrastructure.ai.actividad.ActividadRegistry;
import jpap.dev.io_api.infrastructure.ai.actividad.EtiquetaMetodo;
import jpap.dev.io_api.infrastructure.ai.actividad.FaseActividad;
import jpap.dev.io_api.infrastructure.ai.dto.SolicitudAprobacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orquesta el ciclo de vida de las solicitudes de aprobación humana:
 *
 *   solicitar() — llamado por las @Tool de resolución. Lanza el workflow HITL en
 *                 background (queda bloqueado en la compuerta) y devuelve la
 *                 solicitud que la UI muestra con botones Aprobar/Rechazar.
 *
 *   decidir()   — llamado por el endpoint REST cuando el estudiante decide.
 *                 Completa el PendingResponse "en caliente" (guía HITL §8), espera
 *                 el desenlace del workflow y evacúa el AgenticScope (guía §9).
 *
 *   limpiarExpiradas() — condición de descarte: aborta solicitudes que el
 *                 estudiante nunca respondió, para no filtrar hilos ni scopes.
 */
@Slf4j
@Component
public class AprobacionHumanaService {

    private static final Duration ESPERA_PENDIENTE = Duration.ofSeconds(5);
    private static final Duration ESPERA_RESOLUCION = Duration.ofSeconds(60);
    private static final Duration EXPIRACION = Duration.ofMinutes(15);

    private final ResolucionAprobadaWorkflow workflow;
    private final SolicitudAprobacionRegistry registry;
    private final ExecutorService hitlExecutor;
    private final ActividadRegistry actividadRegistry;
    // Jackson 3 (tools.jackson): el modeloModificado llega ya deserializado por los
    // convertidores de Spring Boot 4, que son los de Jackson 3.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public AprobacionHumanaService(ResolucionAprobadaWorkflow workflow,
                                   SolicitudAprobacionRegistry registry,
                                   ExecutorService hitlExecutor,
                                   ActividadRegistry actividadRegistry) {
        this.workflow = workflow;
        this.registry = registry;
        this.hitlExecutor = hitlExecutor;
        this.actividadRegistry = actividadRegistry;
    }

    /** Desenlace de una decisión: lo que el endpoint necesita para responder a la UI y retomar al tutor. */
    public record Desenlace(
            String sesionId,
            MetodoResolucion metodo,
            boolean aprobado,
            String comentario,
            String resumenParaTutor,
            ModeloResoluble modelo,                  // el modelo realmente resuelto (puede venir editado desde la UI)
            ResolucionEjecutor.Ejecucion ejecucion   // null si fue rechazada
    ) {}

    /**
     * Crea una solicitud de aprobación y arranca el workflow HITL en background.
     * Si la sesión ya tenía una solicitud pendiente, la reemplaza (la anterior se aborta).
     */
    public SolicitudAprobacion solicitar(String sesionId, ModeloResoluble modelo, MetodoResolucion metodo) {
        registry.buscarPorSesion(sesionId)
                .ifPresent(previa -> abortar(previa, "reemplazada por una nueva solicitud de la misma sesión"));

        String solicitudId = UUID.randomUUID().toString();
        SolicitudAprobacionRegistry.Solicitud solicitud =
                registry.registrar(solicitudId, sesionId, modelo, metodo);

        CompletableFuture<String> ejecucion = CompletableFuture.supplyAsync(
                () -> workflow.resolver(solicitudId, modelo, metodo), hitlExecutor);
        solicitud.adjuntarEjecucion(ejecucion);

        // Aquí, y no en SolicitudAprobacionHelper: este es el punto por el que pasan las
        // once tools de resolución, y el único que ya tiene sesión, modelo y método juntos.
        actividadRegistry.publicar(sesionId, FaseActividad.PREPARANDO, EtiquetaMetodo.de(metodo, modelo));

        log.info("[HITL] solicitud {} creada — sesion={}, metodo={}", solicitudId, sesionId, metodo);
        return new SolicitudAprobacion(solicitudId, metodo, modelo);
    }

    /**
     * Completa la compuerta HITL con la decisión del estudiante y espera el desenlace
     * del workflow (si aprobó, incluye el resultado del solver).
     */
    public Desenlace decidir(String solicitudId, boolean aprobado, String comentario) {
        return decidir(solicitudId, aprobado, comentario, null);
    }

    /**
     * Completa la compuerta HITL con la decisión del estudiante y espera el desenlace
     * del workflow (si aprobó, incluye el resultado del solver). Si viene un modeloModificado
     * desde la UI, reemplaza el modelo guardado en la solicitud para asegurar 100% concordancia.
     */
    public Desenlace decidir(String solicitudId, boolean aprobado, String comentario, JsonNode modeloModificado) {
        SolicitudAprobacionRegistry.Solicitud solicitud = registry.obtener(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una solicitud de aprobación pendiente con id " + solicitudId));

        if (aprobado && modeloModificado != null && !modeloModificado.isNull()) {
            try {
                Class<? extends ModeloResoluble> claseModelo = MetodoResolucion.claseModeloPorMetodo(solicitud.metodo());
                ModeloResoluble nuevoModelo = objectMapper.treeToValue(modeloModificado, claseModelo);
                solicitud.actualizarModelo(nuevoModelo);
                log.info("[HITL] Modelo de solicitud {} sustituido por modeloModificado de UI ({})",
                        solicitudId, claseModelo.getSimpleName());
            } catch (Exception e) {
                log.warn("[HITL] No se pudo deserializar modeloModificado para {}: {}", solicitud.metodo(), e.getMessage());
            }
        }

        // Se anuncia ANTES de abrir la compuerta: el solver corre en un hilo virtual de
        // fondo mientras este hilo espera, así que si se publicara después la UI nunca
        // llegaría a ver la fase. Un rechazo no ejecuta nada, y por eso no la publica.
        if (aprobado) {
            actividadRegistry.publicar(solicitud.sesionId(), FaseActividad.RESOLVIENDO,
                    EtiquetaMetodo.de(solicitud.metodo(), solicitud.modelo()));
        }

        try {
            // Equivalente a scope.completePendingResponse(solicitudId, decision):
            // completamos la referencia directa para no depender del timing del registro del scope
            esperarPendiente(solicitud).complete(new DecisionAprobacion(aprobado, comentario));

            String resumen = solicitud.ejecucion().get(ESPERA_RESOLUCION.toSeconds(), TimeUnit.SECONDS);

            log.info("[HITL] solicitud {} decidida — aprobado={}", solicitudId, aprobado);
            return new Desenlace(solicitud.sesionId(), solicitud.metodo(), aprobado, comentario,
                    resumen, solicitud.modelo(), solicitud.resultado());
        } catch (TimeoutException e) {
            throw new IllegalStateException("El solver no terminó dentro del tiempo esperado", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando el desenlace de la solicitud", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("El workflow de resolución falló: " + e.getCause().getMessage(), e);
        } finally {
            registry.eliminar(solicitudId);
            workflow.evictAgenticScope(solicitudId);
        }
    }

    /**
     * Condición de descarte (guía HITL §9): aborta las solicitudes que llevan más
     * de EXPIRACION sin decisión y evacúa sus scopes para no acumular esperas huérfanas.
     */
    @Scheduled(fixedDelay = 60_000)
    public void limpiarExpiradas() {
        Instant limite = Instant.now().minus(EXPIRACION);
        for (SolicitudAprobacionRegistry.Solicitud solicitud : registry.anterioresA(limite)) {
            log.info("[HITL] solicitud {} expirada sin decisión — se descarta", solicitud.solicitudId());
            abortar(solicitud, "expiró sin decisión del estudiante");
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void abortar(SolicitudAprobacionRegistry.Solicitud solicitud, String motivo) {
        var pendiente = solicitud.cancelar();
        if (pendiente != null) {
            pendiente.completeExceptionally(
                    new IllegalStateException("Solicitud de aprobación abortada: " + motivo));
        }
        registry.eliminar(solicitud.solicitudId());
        workflow.evictAgenticScope(solicitud.solicitudId());
    }

    private dev.langchain4j.agentic.internal.PendingResponse<DecisionAprobacion> esperarPendiente(
            SolicitudAprobacionRegistry.Solicitud solicitud) {
        // El workflow en background adjunta el pendiente milisegundos después de
        // crearse la solicitud; este poll solo cubre esa ventana de arranque.
        Instant limite = Instant.now().plus(ESPERA_PENDIENTE);
        while (solicitud.pendiente() == null) {
            if (Instant.now().isAfter(limite)) {
                throw new IllegalStateException("La compuerta de aprobación no se inicializó a tiempo");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando la compuerta de aprobación", e);
            }
        }
        return solicitud.pendiente();
    }
}
