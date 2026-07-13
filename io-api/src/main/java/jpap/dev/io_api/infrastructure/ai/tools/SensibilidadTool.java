package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.actividad.ActividadRegistry;
import jpap.dev.io_api.infrastructure.ai.actividad.FaseActividad;
import jpap.dev.io_api.infrastructure.ai.sensibilidad.SensibilidadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Devuelve el análisis de sensibilidad REAL del último modelo LP resuelto en la sesión.
 *
 * A diferencia de las tools de resolución, esta NO pasa por la compuerta HITL: no ejecuta
 * ningún solver, solo relee de la BD un resultado que el estudiante ya aprobó. Por eso
 * tampoco lleva parámetros: el sesionId lo pone el controlador en el ChatContextStore.
 *
 * Su razón de ser es evitar que el tutor invente números. Si no hay nada que leer, la tool
 * devuelve un texto que se lo dice explícitamente al LLM.
 */
@Slf4j
@Component
public class SensibilidadTool {

    private final SensibilidadService sensibilidadService;
    private final ChatContextStore contextStore;
    private final ActividadRegistry actividadRegistry;

    public SensibilidadTool(SensibilidadService sensibilidadService,
                            ChatContextStore contextStore,
                            ActividadRegistry actividadRegistry) {
        this.sensibilidadService = sensibilidadService;
        this.contextStore = contextStore;
        this.actividadRegistry = actividadRegistry;
    }

    @Tool("""
            Obtiene el análisis de sensibilidad (holguras, valores marginales y rangos) del último
            modelo de Programación Lineal resuelto en esta conversación, con los números REALES.
            INVOCA esto SIEMPRE que el estudiante pregunte por el análisis de sensibilidad, las
            holguras, los precios sombra, qué recurso es el cuello de botella, si le conviene comprar
            más de un recurso, o hasta cuánto puede variar un coeficiente sin que cambie el plan.
            NUNCA respondas esas preguntas de memoria: los datos correctos solo salen de aquí.
            No necesita parámetros y no ejecuta ningún solver.
            """)
    public String explicarSensibilidad() {
        String sesionId = contextStore.obtener().sesionId;
        actividadRegistry.publicar(sesionId, FaseActividad.EXPLICANDO);

        log.info("[TOOL] explicarSensibilidad — sesion={}", sesionId);
        if (sesionId == null) {
            return "NO HAY DATOS DE SENSIBILIDAD disponibles. Dilo con honestidad al estudiante "
                    + "y ofrécele resolver el modelo. JAMÁS inventes los números.";
        }
        return sensibilidadService.explicarUltimaResolucion(sesionId);
    }
}
