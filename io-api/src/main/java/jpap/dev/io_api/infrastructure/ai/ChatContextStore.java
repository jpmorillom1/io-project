package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;
import org.springframework.stereotype.Component;

/**
 * Almacena los datos estructurados que las @Tool escriben durante una invocación
 * de TutorAiService.chat(). El controlador los lee al terminar y los incluye en ChatResponse.
 *
 * Usa ThreadLocal porque LangChain4j en modo síncrono ejecuta las tools en el mismo hilo.
 * El controlador SIEMPRE debe llamar limpiar() en un bloque finally.
 */
@Component
public class ChatContextStore {

    private final ThreadLocal<DatosRespuesta> local = new ThreadLocal<>();

    public void iniciar() {
        local.set(new DatosRespuesta());
    }

    public DatosRespuesta obtener() {
        DatosRespuesta datos = local.get();
        return datos != null ? datos : new DatosRespuesta();
    }

    public void limpiar() {
        local.remove();
    }

    public static class DatosRespuesta {
        public ModeloLP modeloSugerido;
        public ValidacionResponse validacion;
        public SolveResult<SolucionLP> resultado;
        public SolveResult<SolucionGrafica> resultadoGrafico;
    }
}
