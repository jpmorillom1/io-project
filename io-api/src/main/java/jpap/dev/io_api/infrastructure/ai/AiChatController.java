package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore.DatosRespuesta;
import jpap.dev.io_api.infrastructure.ai.dto.ChatRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ChatResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ModeloSugeridoResponse;
import jpap.dev.io_api.infrastructure.ai.dto.SugerirModeloRequest;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ValidarModeloRequest;
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
 * POST /api/v1/ai/chat           — conversación socrática con memoria de sesión
 * POST /api/v1/ai/sugerir-modelo — extrae un ModeloLP desde descripción en lenguaje natural
 * POST /api/v1/ai/validar-modelo — valida un ModeloLP contra la descripción original
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final TutorAiService tutorAiService;
    private final ModeloAiService modeloAiService;
    private final ChatContextStore contextStore;

    public AiChatController(TutorAiService tutorAiService,
                            ModeloAiService modeloAiService,
                            ChatContextStore contextStore) {
        this.tutorAiService = tutorAiService;
        this.modeloAiService = modeloAiService;
        this.contextStore = contextStore;
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

        contextStore.iniciar();
        try {
            String respuesta = tutorAiService.chat(sesionId, request.mensaje());
            DatosRespuesta datos = contextStore.obtener();

            log.info("[AI/chat] tools invocadas — modelo={} validacion={} resultado={}",
                    datos.modeloSugerido != null,
                    datos.validacion != null,
                    datos.resultado != null);
            log.debug("[AI/chat] respuesta: {}", respuesta);

            return ResponseEntity.ok(new ChatResponse(
                    sesionId, respuesta,
                    datos.modeloSugerido,
                    datos.validacion,
                    datos.resultado
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
