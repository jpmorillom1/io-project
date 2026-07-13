package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore.DatosRespuesta;
import jpap.dev.io_api.infrastructure.ai.actividad.Actividad;
import jpap.dev.io_api.infrastructure.ai.actividad.ActividadRegistry;
import jpap.dev.io_api.infrastructure.ai.actividad.FaseActividad;
import jpap.dev.io_api.infrastructure.ai.dto.ChatRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ChatResponse;
import jpap.dev.io_api.infrastructure.ai.dto.DecisionAprobacionRequest;
import jpap.dev.io_api.infrastructure.ai.dto.HistorialSesion;
import jpap.dev.io_api.infrastructure.ai.dto.ModeloSugeridoResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ResumenSesion;
import jpap.dev.io_api.infrastructure.ai.dto.SugerirModeloRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ValidarModeloRequest;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.supervisor.TutorSupervisorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de la capa de IA.
 *
 * POST /api/v1/ai/chat                       — conversación socrática con memoria de sesión
 * POST /api/v1/ai/chat/aprobacion            — decisión humana (HITL) sobre una solicitud de resolución
 * GET  /api/v1/ai/sesiones                   — conversaciones de la barra lateral
 * GET  /api/v1/ai/chat/{sesionId}/historial  — transcript + último resultado, para rehidratar la UI
 * POST /api/v1/ai/sugerir-modelo             — extrae un ModeloLP desde descripción en lenguaje natural
 * POST /api/v1/ai/validar-modelo             — valida un ModeloLP contra la descripción original
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    /** Mensaje que ve el estudiante cuando el LLM falla incluso tras los reintentos. */
    private static final String MENSAJE_ERROR_LLM =
            "Lo siento, tuve un problema técnico al procesar tu mensaje. "
            + "Por favor, envíalo de nuevo — la conversación sigue guardada.";

    private static final String MENSAJE_ERROR_LLM_CON_RESULTADO =
            "El método se ejecutó correctamente y el procedimiento ya está en pantalla, "
            + "pero tuve un problema técnico al preparar la explicación. "
            + "Pídeme que te explique el resultado y lo retomamos.";

    private final TutorSupervisorService tutorSupervisorService;
    private final ModeloAiService modeloAiService;
    private final ChatContextStore contextStore;
    private final AprobacionHumanaService aprobacionService;
    private final ChatHistorialService historialService;
    private final TituloSesionService tituloService;
    private final ActividadRegistry actividadRegistry;

    public AiChatController(TutorSupervisorService tutorSupervisorService,
                            ModeloAiService modeloAiService,
                            ChatContextStore contextStore,
                            AprobacionHumanaService aprobacionService,
                            ChatHistorialService historialService,
                            TituloSesionService tituloService,
                            ActividadRegistry actividadRegistry) {
        this.tutorSupervisorService = tutorSupervisorService;
        this.modeloAiService = modeloAiService;
        this.contextStore = contextStore;
        this.aprobacionService = aprobacionService;
        this.historialService = historialService;
        this.tituloService = tituloService;
        this.actividadRegistry = actividadRegistry;
    }

    /**
     * Conversación socrática principal.
     * La IA guía al estudiante y, cuando el modelo esté validado y se pida explícitamente,
     * invoca resolverSimplex internamente y explica el resultado.
     *
     * Body ejemplo:
     * { "sesionId": null, "mensaje": "Quiero resolver un problema de mezcla de productos" }
     *
     * Para continuar la sesión, reenvía el mismo sesionId retornado en la primera respuesta.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // El sesionId es la clave de chat_memory (columna UUID). Un id que no lo sea
        // arranca sesión nueva en vez de reventar con un 400.
        boolean sesionNueva = !esUuid(request.sesionId());
        String sesionId = sesionNueva ? UUID.randomUUID().toString() : request.sesionId();

        log.info("[AI/chat] sesionId={} nueva={} mensajeLen={}",
                sesionId, sesionNueva, request.mensaje().length());
        log.debug("[AI/chat] mensaje: {}", request.mensaje());

        // Debe existir la fila de sesión antes de llamar al agente: chat_memory tiene FK contra ella.
        boolean creada = historialService.asegurarSesion(sesionId, request.mensaje());
        if (creada) {
            // Fuera de la transacción de asegurarSesion: el hilo de fondo hace su propio
            // UPDATE y no vería una fila que aún no ha comitado.
            tituloService.generarEnBackground(UUID.fromString(sesionId), request.mensaje());
        }

        contextStore.iniciar(sesionId);
        actividadRegistry.publicar(sesionId, FaseActividad.PENSANDO);
        try {
            String respuesta = tutorSupervisorService.chat(sesionId, request.mensaje());
            DatosRespuesta datos = contextStore.obtener();

            log.info("[AI/chat] tools invocadas — modelo={} validacion={} solicitud={} ",
                    datos.modeloSugerido != null,
                    datos.validacion != null,
                    datos.solicitudAprobacion != null);
            log.debug("[AI/chat] respuesta: {}", respuesta);

            registrarTurno(sesionId, request.mensaje(), respuesta,
                    datos.solicitudAprobacion != null ? datos.solicitudAprobacion.metodo().name() : null);

            return ResponseEntity.ok(new ChatResponse(
                    sesionId, respuesta,
                    datos.modeloSugerido,
                    datos.validacion,
                    datos.resultado,
                    datos.resultadoGrafico,
                    datos.resultadoTransporte,
                    datos.resultadoRed,
                    datos.resultadoEntero,
                    datos.resultadoInventario,
                    datos.resultadoDinamica,
                    datos.solicitudAprobacion
            ));
        } catch (RuntimeException e) {
            // El error crudo del proveedor LLM jamás debe llegar al chat: los reintentos
            // ya se agotaron en RetryingChatModel, aquí solo queda degradar con gracia.
            log.error("[AI/chat] fallo del LLM tras reintentos — sesionId={}", sesionId, e);
            registrarTurno(sesionId, request.mensaje(), MENSAJE_ERROR_LLM, null);
            return ResponseEntity.ok(new ChatResponse(
                    sesionId, MENSAJE_ERROR_LLM,
                    null, null, null, null, null, null, null, null, null, null
            ));
        } finally {
            contextStore.limpiar();
            actividadRegistry.limpiar(sesionId);
        }
    }

    /**
     * Qué está haciendo Pivot en esta sesión ahora mismo, para que la UI lo anuncie
     * mientras espera. La UI sondea este endpoint durante el turno.
     *
     * 204 significa "no hay ningún turno en curso" — no es un error.
     */
    @GetMapping("/chat/{sesionId}/actividad")
    public ResponseEntity<Actividad> actividad(@PathVariable String sesionId) {
        return actividadRegistry.actual(sesionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Human-in-the-Loop: el estudiante aprueba o rechaza la solicitud de resolución
     * que el tutor dejó pendiente (ChatResponse.solicitudAprobacion).
     *
     * - Aprobar:  completa la compuerta HITL → el workflow ejecuta el solver → se
     *             informa al tutor con el resumen para que explique el resultado.
     * - Rechazar: no se ejecuta ningún solver; el comentario del estudiante
     *             re-alimenta al tutor para corregir el modelo.
     *
     * Body ejemplo:
     * { "solicitudId": "...", "aprobado": true, "comentario": null }
     */
    @PostMapping("/chat/aprobacion")
    public ResponseEntity<ChatResponse> aprobacion(@RequestBody DecisionAprobacionRequest request) {
        log.info("[AI/chat/aprobacion] solicitudId={} aprobado={}",
                request.solicitudId(), request.aprobado());

        AprobacionHumanaService.Desenlace desenlace =
                aprobacionService.decidir(request.solicitudId(), request.aprobado(), request.comentario(), request.modeloModificado());

        // Reanudar la conversación: el tutor recibe el desenlace como mensaje de sistema
        contextStore.iniciar(desenlace.sesionId());
        // El solver ya corrió dentro de decidir(); lo que queda es que el tutor lo explique.
        // La fase RESOLVIENDO la publicó AprobacionHumanaService antes de abrir la compuerta.
        actividadRegistry.publicar(desenlace.sesionId(), FaseActividad.EXPLICANDO);
        var ejecucion = desenlace.ejecucion();
        String mensajeSistema = mensajeDeDesenlace(desenlace);

        // La evidencia del solver se guarda aunque el tutor falle al explicarla.
        registrarProblemaResuelto(desenlace);

        try {
            String respuesta = tutorSupervisorService.chat(desenlace.sesionId(), mensajeSistema);
            DatosRespuesta datos = contextStore.obtener();

            registrarTurno(desenlace.sesionId(), mensajeSistema, respuesta, desenlace.metodo().name());

            return ResponseEntity.ok(new ChatResponse(
                    desenlace.sesionId(), respuesta,
                    datos.modeloSugerido,
                    datos.validacion,
                    ejecucion != null ? ejecucion.resultado() : null,
                    ejecucion != null ? ejecucion.resultadoGrafico() : null,
                    ejecucion != null ? ejecucion.resultadoTransporte() : null,
                    ejecucion != null ? ejecucion.resultadoRed() : null,
                    ejecucion != null ? ejecucion.resultadoEntero() : null,
                    ejecucion != null ? ejecucion.resultadoInventario() : null,
                    ejecucion != null ? ejecucion.resultadoDinamica() : null,
                    datos.solicitudAprobacion
            ));
        } catch (RuntimeException e) {
            // El solver ya corrió (si fue aprobado): entregamos igual el resultado a la UI
            // aunque el tutor no haya podido generar la explicación.
            log.error("[AI/chat/aprobacion] fallo del LLM tras reintentos — sesionId={}",
                    desenlace.sesionId(), e);
            String respuesta = ejecucion != null
                    ? MENSAJE_ERROR_LLM_CON_RESULTADO
                    : MENSAJE_ERROR_LLM;
            registrarTurno(desenlace.sesionId(), mensajeSistema, respuesta, desenlace.metodo().name());
            return ResponseEntity.ok(new ChatResponse(
                    desenlace.sesionId(), respuesta,
                    null, null,
                    ejecucion != null ? ejecucion.resultado() : null,
                    ejecucion != null ? ejecucion.resultadoGrafico() : null,
                    ejecucion != null ? ejecucion.resultadoTransporte() : null,
                    ejecucion != null ? ejecucion.resultadoRed() : null,
                    ejecucion != null ? ejecucion.resultadoEntero() : null,
                    ejecucion != null ? ejecucion.resultadoInventario() : null,
                    ejecucion != null ? ejecucion.resultadoDinamica() : null,
                    null
            ));
        } finally {
            contextStore.limpiar();
            actividadRegistry.limpiar(desenlace.sesionId());
        }
    }

    /**
     * Conversaciones de la barra lateral, la más reciente primero.
     *
     * No hay usuarios: la lista es global. La purga por inactividad
     * (ChatHistorialService#purgarSesionesInactivas) la mantiene acotada.
     */
    @GetMapping("/sesiones")
    public ResponseEntity<List<ResumenSesion>> sesiones() {
        List<ResumenSesion> sesiones = historialService.listarSesiones();
        log.debug("[AI/sesiones] {} conversaciones", sesiones.size());
        return ResponseEntity.ok(sesiones);
    }

    /**
     * Todo lo necesario para reabrir una conversación: el transcript que rehidrata el chat
     * y el último problema resuelto que rehidrata el workspace.
     *
     * 404 significa "ese sesionId ya no existe": el cliente debe descartarlo y empezar
     * una sesión nueva.
     */
    @GetMapping("/chat/{sesionId}/historial")
    public ResponseEntity<HistorialSesion> historial(@PathVariable String sesionId) {
        return historialService.historialCompleto(sesionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.info("[AI/chat/historial] sesión desconocida {}", sesionId);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Extrae un ModeloLP estructurado desde la descripción del problema en lenguaje natural.
     * Devuelve el modelo como SUGERENCIA — el usuario debe revisarlo y modificarlo.
     *
     * Body ejemplo:
     * { "descripcionProblema": "Maximizar Z=3x1+5x2 sujeto a x1<=4, 2x2<=12, 3x1+5x2<=25, x1,x2>=0" }
     */
    @PostMapping("/sugerir-modelo")
    public ResponseEntity<ModeloSugeridoResponse> sugerirModelo(
            @RequestBody SugerirModeloRequest request) {
        log.info("[AI/sugerir-modelo] descripcionLen={}", request.descripcionProblema().length());
        log.debug("[AI/sugerir-modelo] descripcion: {}", request.descripcionProblema());

        ModeloSugeridoResponse respuesta = modeloAiService.extraerModelo(request.descripcionProblema());

        log.info("[AI/sugerir-modelo] modelo extraído — vars={}, advertencias={}",
                respuesta.modelo() != null ? respuesta.modelo().variables() : "null",
                respuesta.advertencias().size());
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Valida el modelo propuesto por el estudiante contra la descripción original.
     * Si hay errores devuelve modeloCorregido con la versión corregida.
     *
     * Body ejemplo:
     * {
     *   "descripcionProblema": "Maximizar ganancia con x1=mesas, x2=sillas...",
     *   "modelo": { "variables": ["x1","x2"], "objetivo": {...}, "restricciones": [...] }
     * }
     */
    @PostMapping("/validar-modelo")
    public ResponseEntity<ValidacionResponse> validarModelo(
            @RequestBody ValidarModeloRequest request) {
        log.info("[AI/validar-modelo] vars={}, restricciones={}",
                request.modelo().variables(), request.modelo().restricciones().size());

        String contexto = formatearParaValidacion(request);
        ValidacionResponse respuesta = modeloAiService.validarModelo(contexto);

        log.info("[AI/validar-modelo] esValido={}, errores={}",
                respuesta.esValido(), respuesta.erroresEncontrados().size());
        if (!respuesta.esValido()) {
            log.debug("[AI/validar-modelo] errores: {}", respuesta.erroresEncontrados());
        }
        return ResponseEntity.ok(respuesta);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private boolean esUuid(String valor) {
        if (valor == null || valor.isBlank()) return false;
        try {
            UUID.fromString(valor);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * La auditoría es best-effort: un fallo escribiendo el historial no puede convertir
     * una respuesta buena del tutor en un 500.
     */
    private void registrarTurno(String sesionId, String prompt, String respuesta, String toolLlamada) {
        try {
            String modulo = tutorSupervisorService.moduloDeSesion(sesionId).name();
            historialService.registrarTurno(sesionId, modulo, prompt, respuesta, toolLlamada);
        } catch (RuntimeException e) {
            log.warn("[AI] no se pudo registrar el turno de la sesión {}: {}", sesionId, e.getMessage());
        }
    }

    private void registrarProblemaResuelto(AprobacionHumanaService.Desenlace d) {
        if (!d.aprobado() || d.ejecucion() == null) return;
        try {
            historialService.registrarProblemaResuelto(
                    d.sesionId(), d.metodo(), d.modelo(), d.ejecucion());
        } catch (RuntimeException e) {
            log.warn("[AI] no se pudo registrar el problema resuelto de la sesión {}: {}",
                    d.sesionId(), e.getMessage());
        }
    }

    private String mensajeDeDesenlace(AprobacionHumanaService.Desenlace d) {
        if (d.aprobado()) {
            return """
                    [SISTEMA] El estudiante APROBÓ el modelo en la interfaz y el método %s ya se ejecutó.
                    Este es el resultado del solver:

                    %s

                    La interfaz ya muestra el procedimiento completo. Presenta el resultado conectándolo
                    con el problema real, pregunta cómo lo interpreta, ofrece revisar las iteraciones y
                    ofrece explicarle qué recursos quedaron como cuello de botella y cuánto valdría
                    conseguir más de cada uno.
                    """.formatted(d.metodo(), d.resumenParaTutor());
        }
        String comentario = d.comentario() != null && !d.comentario().isBlank()
                ? "Su comentario: \"" + d.comentario() + "\""
                : "No dejó comentario.";
        return """
                [SISTEMA] El estudiante RECHAZÓ la solicitud de resolver — el solver NO se ejecutó.
                %s
                Retoma la conversación socráticamente: pregunta qué quiere ajustar del modelo y,
                si su comentario da suficiente información, propone la corrección con registrarModeloSugerido.
                NO vuelvas a pedir resolver hasta que el estudiante lo pida de nuevo.
                """.formatted(comentario);
    }

    private String formatearParaValidacion(ValidarModeloRequest req) {
        var m = req.modelo();
        var sb = new StringBuilder();

        sb.append("DESCRIPCIÓN ORIGINAL DEL PROBLEMA:\n").append(req.descripcionProblema()).append("\n\n");
        sb.append("MODELO PROPUESTO POR EL ESTUDIANTE:\n");
        sb.append("Variables: ").append(String.join(", ", m.variables())).append("\n");

        FuncionObjetivo obj = m.objetivo();
        sb.append("Función objetivo (").append(obj.tipo()).append("): Z = ");
        sb.append(formatearTerminos(obj.coeficientes(), m.variables())).append("\n");

        sb.append("Restricciones:\n");
        List<Restriccion> restricciones = m.restricciones();
        for (int i = 0; i < restricciones.size(); i++) {
            Restriccion r = restricciones.get(i);
            String signo = r.tipo() == TipoRestriccion.LEQ ? " <= "
                         : r.tipo() == TipoRestriccion.GEQ ? " >= " : " = ";
            sb.append("  ").append(i + 1).append(". ")
              .append(formatearTerminos(r.coeficientes(), m.variables()))
              .append(signo).append(r.rhs()).append("\n");
        }
        sb.append("  No negatividad: todas las variables >= 0\n");

        return sb.toString();
    }

    private String formatearTerminos(List<Double> coefs, List<String> vars) {
        var sb = new StringBuilder();
        for (int i = 0; i < coefs.size(); i++) {
            double c = coefs.get(i);
            if (i > 0 && c >= 0) sb.append(" + ");
            else if (i > 0) sb.append(" ");
            sb.append(c).append(vars.get(i));
        }
        return sb.toString();
    }
}
