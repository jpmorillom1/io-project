package jpap.dev.io_api.infrastructure.ai.hitl;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import jpap.dev.io_api.domain.common.ModeloResoluble;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuración del Human-in-the-Loop (langchain4j-agentic).
 *
 * El workflow es una secuencia de dos agentes no-IA:
 *
 *   1. compuertaAprobacion (HumanInTheLoop) — publica un PendingResponse en el
 *      AgenticScope bajo la clave "decision". No consume ningún hilo de fondo:
 *      es un futuro incompleto que el endpoint REST completa con la decisión
 *      real del estudiante (guía HITL §7-§8).
 *
 *   2. resolverAprobado (agentAction) — lee "decision" del scope; esa lectura
 *      BLOQUEA (DelayedResponse.blockingGet) hasta que llegue la decisión.
 *      Solo si el estudiante aprobó ejecuta el solver. Esta es la garantía
 *      estructural: ningún solver corre sin el clic humano, sin importar lo
 *      que decida el LLM.
 */
@Slf4j
@Configuration
@EnableScheduling
public class HitlConfig {

    /**
     * Hilos virtuales para las invocaciones del workflow: cada solicitud bloquea
     * su hilo esperando la decisión humana, y bloquear un hilo virtual es barato.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService hitlExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ResolucionAprobadaWorkflow resolucionAprobadaWorkflow(ResolucionEjecutor ejecutor,
                                                                 SolicitudAprobacionRegistry registry) {
        HumanInTheLoop compuertaAprobacion = AgenticServices.humanInTheLoopBuilder()
                .description("Espera la aprobación o rechazo explícito del estudiante antes de resolver el modelo LP")
                .outputKey("decision")
                .responseProvider(scope -> {
                    String solicitudId = (String) scope.memoryId();
                    SolicitudAprobacionRegistry.Solicitud solicitud = registry.obtener(solicitudId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Solicitud de aprobación no registrada: " + solicitudId));
                    PendingResponse<DecisionAprobacion> pendiente = new PendingResponse<>(solicitudId);
                    if (!solicitud.adjuntarPendienteSiActiva(pendiente)) {
                        throw new IllegalStateException("Solicitud de aprobación cancelada: " + solicitudId);
                    }
                    log.info("[HITL] compuerta activa — esperando decisión humana para solicitud {}", solicitudId);
                    return pendiente;
                })
                .build();

        var resolverAprobado = AgenticServices.agentAction(scope -> {
            // Bloquea hasta que el endpoint complete el PendingResponse con la decisión
            DecisionAprobacion decision = (DecisionAprobacion) scope.readState("decision");
            String solicitudId = (String) scope.memoryId();

            if (!decision.aprobado()) {
                log.info("[HITL] solicitud {} RECHAZADA — no se ejecuta ningún solver", solicitudId);
                scope.writeState("resumen", "El estudiante RECHAZÓ el modelo. No se ejecutó ningún solver."
                        + (decision.comentario() != null && !decision.comentario().isBlank()
                            ? " Comentario: " + decision.comentario() : ""));
                return;
            }

            ModeloResoluble modelo = (ModeloResoluble) scope.readState("modelo");
            MetodoResolucion metodo = (MetodoResolucion) scope.readState("metodo");
            try {
                ResolucionEjecutor.Ejecucion ejecucion = ejecutor.ejecutar(modelo, metodo);
                registry.registrarResultado(solicitudId, ejecucion);
                scope.writeState("resumen", ejecucion.resumenParaTutor());
            } catch (Exception e) {
                log.error("[HITL] error al ejecutar el solver aprobado (solicitud {})", solicitudId, e);
                scope.writeState("resumen", "ERROR al ejecutar el solver " + metodo + ": " + e.getMessage());
            }
        });

        return AgenticServices.sequenceBuilder(ResolucionAprobadaWorkflow.class)
                .name("resolucion-aprobada")
                .description("Resolución de modelos LP con compuerta de aprobación humana")
                .subAgents(compuertaAprobacion, resolverAprobado)
                .outputKey("resumen")
                .build();
    }
}
