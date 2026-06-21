package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que el tutor invoca cuando ha evaluado el modelo del estudiante.
 * Escribe ValidacionResponse en ChatContextStore para que la UI muestre
 * los errores inline en el formulario sin necesidad de un endpoint separado.
 *
 * Si el modelo tiene errores y el tutor quiere proponer la versión corregida,
 * puede encadenar una llamada a SugerirModeloTool con el modelo corregido.
 */
@Slf4j
@Component
public class ValidarModeloTool {

    private final ChatContextStore contextStore;

    public ValidarModeloTool(ChatContextStore contextStore) {
        this.contextStore = contextStore;
    }

    @Tool("""
            Registra el resultado de tu validación del modelo LP del estudiante.
            INVOCA esto cuando el estudiante haya presentado un modelo (completo o parcial)
            y tú hayas evaluado si es correcto respecto al enunciado original.
            La UI mostrará los errores encontrados directamente en el formulario.
            Si el modelo tiene errores y quieres proponer una versión corregida,
            llama también a registrarModeloSugerido con el modelo corregido.
            """)
    public String registrarValidacion(
            @P("true si el modelo es correcto, false si tiene errores")
            boolean esValido,

            @P("Análisis general de la validación en 1-2 oraciones")
            String analisis,

            @P("Lista de errores encontrados. Vacía si el modelo es correcto")
            List<String> erroresEncontrados,

            @P("Lista de sugerencias para guiar al estudiante. Vacía si el modelo es correcto")
            List<String> sugerencias
    ) {
        ValidacionResponse validacion = new ValidacionResponse(
                esValido, analisis, erroresEncontrados, sugerencias, null
        );

        contextStore.obtener().validacion = validacion;

        log.info("[TOOL] registrarValidacion — esValido={}, errores={}",
                esValido, erroresEncontrados.size());
        if (!esValido) {
            log.debug("[TOOL] registrarValidacion errores: {}", erroresEncontrados);
        }

        return esValido
                ? "Validación registrada: modelo correcto."
                : "Validación registrada: " + erroresEncontrados.size() + " error(es) mostrados en la interfaz.";
    }
}
