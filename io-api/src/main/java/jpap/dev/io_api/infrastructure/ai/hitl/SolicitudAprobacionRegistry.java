package jpap.dev.io_api.infrastructure.ai.hitl;

import dev.langchain4j.agentic.internal.PendingResponse;
import jpap.dev.io_api.domain.common.ModeloResoluble;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en RAM de las solicitudes de aprobación humana en vuelo.
 * Cada solicitud referencia el PendingResponse de la compuerta HITL (para
 * completarlo desde el endpoint REST) y el futuro de la ejecución del workflow
 * (para esperar el resultado tras la decisión).
 *
 * Como la memoria de chat, se pierde al reiniciar — suficiente para sesiones
 * de tutoría. La persistencia requeriría un AgenticScopeStore (guía HITL §7).
 */
@Component
public class SolicitudAprobacionRegistry {

    /**
     * Estado mutable de una solicitud en vuelo. Los campos volatile se escriben
     * desde el hilo del workflow (background) y se leen desde el hilo del endpoint.
     */
    public static final class Solicitud {
        private final String solicitudId;
        private final String sesionId;
        private final ModeloResoluble modelo;
        private final MetodoResolucion metodo;
        private final Instant creadaEn = Instant.now();

        private volatile PendingResponse<DecisionAprobacion> pendiente;
        private volatile CompletableFuture<String> ejecucion;
        private volatile ResolucionEjecutor.Ejecucion resultado;
        private boolean cancelada;

        private Solicitud(String solicitudId, String sesionId, ModeloResoluble modelo, MetodoResolucion metodo) {
            this.solicitudId = solicitudId;
            this.sesionId = sesionId;
            this.modelo = modelo;
            this.metodo = metodo;
        }

        public String solicitudId() { return solicitudId; }
        public String sesionId() { return sesionId; }
        public ModeloResoluble modelo() { return modelo; }
        public MetodoResolucion metodo() { return metodo; }
        public Instant creadaEn() { return creadaEn; }
        public PendingResponse<DecisionAprobacion> pendiente() { return pendiente; }
        public CompletableFuture<String> ejecucion() { return ejecucion; }
        public ResolucionEjecutor.Ejecucion resultado() { return resultado; }

        void adjuntarEjecucion(CompletableFuture<String> ejecucion) { this.ejecucion = ejecucion; }
        void registrarResultado(ResolucionEjecutor.Ejecucion resultado) { this.resultado = resultado; }

        /**
         * Adjunta el PendingResponse creado por la compuerta HITL, salvo que la
         * solicitud ya haya sido cancelada. Sincronizado con cancelar() para que
         * ninguna compuerta quede esperando una decisión que nunca podrá llegar.
         */
        synchronized boolean adjuntarPendienteSiActiva(PendingResponse<DecisionAprobacion> pendiente) {
            if (cancelada) {
                return false;
            }
            this.pendiente = pendiente;
            return true;
        }

        /** Marca la solicitud como cancelada y devuelve el pendiente a abortar (o null). */
        synchronized PendingResponse<DecisionAprobacion> cancelar() {
            cancelada = true;
            return pendiente;
        }
    }

    private final Map<String, Solicitud> solicitudes = new ConcurrentHashMap<>();

    public Solicitud registrar(String solicitudId, String sesionId, ModeloResoluble modelo, MetodoResolucion metodo) {
        Solicitud solicitud = new Solicitud(solicitudId, sesionId, modelo, metodo);
        solicitudes.put(solicitudId, solicitud);
        return solicitud;
    }

    public Optional<Solicitud> obtener(String solicitudId) {
        return Optional.ofNullable(solicitudes.get(solicitudId));
    }

    public Optional<Solicitud> buscarPorSesion(String sesionId) {
        return solicitudes.values().stream()
                .filter(s -> s.sesionId().equals(sesionId))
                .findFirst();
    }

    public List<Solicitud> anterioresA(Instant limite) {
        return solicitudes.values().stream()
                .filter(s -> s.creadaEn().isBefore(limite))
                .toList();
    }

    public void registrarResultado(String solicitudId, ResolucionEjecutor.Ejecucion resultado) {
        Solicitud solicitud = solicitudes.get(solicitudId);
        if (solicitud != null) {
            solicitud.registrarResultado(resultado);
        }
    }

    public void eliminar(String solicitudId) {
        solicitudes.remove(solicitudId);
    }
}
