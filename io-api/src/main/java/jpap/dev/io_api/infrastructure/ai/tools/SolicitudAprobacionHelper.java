package jpap.dev.io_api.infrastructure.ai.tools;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.dto.SolicitudAprobacion;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;

/**
 * Paso común de las @Tool de resolución: crear la solicitud de aprobación humana,
 * publicarla en el ChatContextStore (para que ChatResponse la lleve a la UI) y
 * devolver al LLM la instrucción de que el problema AÚN NO está resuelto.
 */
final class SolicitudAprobacionHelper {

    private SolicitudAprobacionHelper() {}

    static String solicitar(AprobacionHumanaService aprobacionService,
                            ChatContextStore contextStore,
                            ModeloResoluble modelo,
                            MetodoResolucion metodo) {
        String sesionId = contextStore.obtener().sesionId;
        if (sesionId == null) {
            throw new IllegalStateException(
                    "No hay sesión de chat activa — la solicitud de aprobación requiere un sesionId");
        }

        SolicitudAprobacion solicitud = aprobacionService.solicitar(sesionId, modelo, metodo);
        contextStore.obtener().solicitudAprobacion = solicitud;

        return """
                SOLICITUD DE APROBACIÓN ENVIADA — EL PROBLEMA AÚN NO ESTÁ RESUELTO.
                La interfaz ya muestra al estudiante el modelo y el método %s con botones Aprobar/Rechazar.
                Dile al estudiante que revise el modelo mostrado y lo confirme con el botón Aprobar
                para ejecutar el método, o lo rechace si quiere ajustar algo.
                NO inventes ni anticipes resultados: el resultado del solver llegará después,
                en un mensaje [SISTEMA], solo si el estudiante aprueba.
                """.formatted(metodo);
    }
}
