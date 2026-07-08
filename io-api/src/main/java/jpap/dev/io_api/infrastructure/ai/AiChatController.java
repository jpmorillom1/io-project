package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore.DatosRespuesta;
import jpap.dev.io_api.infrastructure.ai.dto.ChatRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ChatResponse;
import jpap.dev.io_api.infrastructure.ai.dto.DecisionAprobacionRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ModeloSugeridoResponse;
import jpap.dev.io_api.infrastructure.ai.dto.SugerirModeloRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ValidarModeloRequest;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de la capa de IA.
 *
 * POST /api/v1/ai/chat            — conversación socrática con memoria de sesión
 * POST /api/v1/ai/chat/aprobacion — decisión humana (HITL) sobre una solicitud de resolución
 * POST /api/v1/ai/sugerir-modelo  — extrae un ModeloLP desde descripción en lenguaje natural
 * POST /api/v1/ai/validar-modelo  — valida un ModeloLP contra la descripción original
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

    private final TutorAiService tutorAiService;
    private final ModeloAiService modeloAiService;
    private final ChatContextStore contextStore;
    private final AprobacionHumanaService aprobacionService;

    public AiChatController(TutorAiService tutorAiService,
                            ModeloAiService modeloAiService,
                            ChatContextStore contextStore,
                            AprobacionHumanaService aprobacionService) {
        this.tutorAiService = tutorAiService;
        this.modeloAiService = modeloAiService;
        this.contextStore = contextStore;
        this.aprobacionService = aprobacionService;
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
        boolean sesionNueva = request.sesionId() == null || request.sesionId().isBlank();
        String sesionId = sesionNueva ? UUID.randomUUID().toString() : request.sesionId();

        log.info("[AI/chat] sesionId={} nueva={} mensajeLen={}",
                sesionId, sesionNueva, request.mensaje().length());
        log.debug("[AI/chat] mensaje: {}", request.mensaje());

        contextStore.iniciar(sesionId);
        try {
            String respuesta = tutorAiService.chat(sesionId, request.mensaje());
            DatosRespuesta datos = contextStore.obtener();

            log.info("[AI/chat] tools invocadas — modelo={} validacion={} solicitud={} ",
                    datos.modeloSugerido != null,
                    datos.validacion != null,
                    datos.solicitudAprobacion != null);
            log.debug("[AI/chat] respuesta: {}", respuesta);

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
                    datos.solicitudAprobacion
            ));
        } catch (RuntimeException e) {
            // El error crudo del proveedor LLM jamás debe llegar al chat: los reintentos
            // ya se agotaron en RetryingChatModel, aquí solo queda degradar con gracia.
            log.error("[AI/chat] fallo del LLM tras reintentos — sesionId={}", sesionId, e);
            return ResponseEntity.ok(new ChatResponse(
                    sesionId, MENSAJE_ERROR_LLM,
                    null, null, null, null, null, null, null, null, null
            ));
        } finally {
            contextStore.limpiar();
        }
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
                aprobacionService.decidir(request.solicitudId(), request.aprobado(), request.comentario());

        // Reanudar la conversación: el tutor recibe el desenlace como mensaje de sistema
        contextStore.iniciar(desenlace.sesionId());
        var ejecucion = desenlace.ejecucion();
        try {
            String respuesta = tutorAiService.chat(desenlace.sesionId(), mensajeDeDesenlace(desenlace));
            DatosRespuesta datos = contextStore.obtener();

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
            return ResponseEntity.ok(new ChatResponse(
                    desenlace.sesionId(), respuesta,
                    null, null,
                    ejecucion != null ? ejecucion.resultado() : null,
                    ejecucion != null ? ejecucion.resultadoGrafico() : null,
                    ejecucion != null ? ejecucion.resultadoTransporte() : null,
                    ejecucion != null ? ejecucion.resultadoRed() : null,
                    ejecucion != null ? ejecucion.resultadoEntero() : null,
                    ejecucion != null ? ejecucion.resultadoInventario() : null,
                    null
            ));
        } finally {
            contextStore.limpiar();
        }
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

    private String mensajeDeDesenlace(AprobacionHumanaService.Desenlace d) {
        if (d.aprobado()) {
            return """
                    [SISTEMA] El estudiante APROBÓ el modelo en la interfaz y el método %s ya se ejecutó.
                    Este es el resultado del solver:

                    %s

                    La interfaz ya muestra el procedimiento completo. Presenta el resultado conectándolo
                    con el problema real, pregunta cómo lo interpreta y ofrece revisar las iteraciones.
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
