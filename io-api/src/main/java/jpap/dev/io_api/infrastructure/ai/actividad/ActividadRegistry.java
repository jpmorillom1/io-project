package jpap.dev.io_api.infrastructure.ai.actividad;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Qué está haciendo Pivot en cada sesión, ahora mismo. Vive en RAM y solo dura lo
 * que dura un turno: es un indicador de progreso, no un registro de auditoría (de
 * eso ya se ocupa {@code interaccion_ia}).
 *
 * No lleva ThreadLocal como {@link jpap.dev.io_api.infrastructure.ai.ChatContextStore}
 * a propósito: quien LEE la actividad es una petición HTTP distinta (el sondeo de la
 * UI), en otro hilo, y el solver del HITL corre además en un hilo virtual de fondo.
 * La clave tiene que ser la sesión, no el hilo.
 *
 * Publicar es best-effort: si algo falla aquí, jamás debe tumbar el turno del chat.
 * Por eso ninguna llamada a {@code publicar} está en la ruta crítica de una respuesta.
 */
@Slf4j
@Component
public class ActividadRegistry {

    /** Colchón de seguridad: una sesión sin turno en curso no debe quedar marcada como ocupada. */
    private static final Duration CADUCIDAD = Duration.ofMinutes(5);

    private record Entrada(Actividad actividad, Instant instante) {}

    private final Map<String, Entrada> porSesion = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    public void publicar(String sesionId, FaseActividad fase) {
        publicar(sesionId, fase, null);
    }

    public void publicar(String sesionId, FaseActividad fase, String detalle) {
        if (sesionId == null) return;
        Actividad actividad = new Actividad(fase, fase.texto(detalle), secuencia.incrementAndGet());
        porSesion.put(sesionId, new Entrada(actividad, Instant.now()));
        log.debug("[ACTIVIDAD] sesion={} -> {}", sesionId, actividad.texto());
    }

    /** Vacío = la sesión no tiene ningún turno en curso. */
    public Optional<Actividad> actual(String sesionId) {
        Entrada entrada = porSesion.get(sesionId);
        if (entrada == null) return Optional.empty();
        // Una entrada caducada es basura de un turno que murió sin pasar por limpiar():
        // devolverla dejaría a la UI anunciando "Resolviendo…" para siempre.
        if (entrada.instante().isBefore(Instant.now().minus(CADUCIDAD))) {
            porSesion.remove(sesionId);
            return Optional.empty();
        }
        return Optional.of(entrada.actividad());
    }

    /** El turno terminó. El controlador DEBE llamarlo en un finally. */
    public void limpiar(String sesionId) {
        if (sesionId != null) porSesion.remove(sesionId);
    }

    /**
     * Red de seguridad por si un turno muere de una forma que se salta el finally
     * (p. ej. el proceso se queda sin hilos). Sin esto, el mapa crecería sin techo.
     */
    @Scheduled(fixedDelay = 60_000)
    public void purgarCaducadas() {
        Instant limite = Instant.now().minus(CADUCIDAD);
        porSesion.entrySet().removeIf(e -> e.getValue().instante().isBefore(limite));
    }
}
