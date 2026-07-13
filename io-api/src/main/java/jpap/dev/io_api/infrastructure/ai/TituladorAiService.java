package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.service.UserMessage;

/**
 * Redacta el nombre con el que una conversación aparece en la barra lateral.
 *
 * Va contra un modelo pequeño y barato (ver AiConfig#tituladorAiService): es una tarea de
 * una línea, sin memoria, sin tools y sin RAG. Nunca debe consumir el presupuesto del tutor.
 */
public interface TituladorAiService {

    @UserMessage("""
            Eres un asistente que nombra conversaciones de una plataforma de Investigación Operativa.

            Escribe un título breve para la conversación que empieza con el mensaje de abajo.

            Reglas:
            - Máximo 6 palabras, en español.
            - Describe el PROBLEMA, no la acción del estudiante. Ej: "Mezcla de productos con dos recursos",
              no "El estudiante quiere resolver un problema".
            - Si reconoces el módulo de IO, úsalo. Ej: "Transporte de 3 plantas a 4 ciudades".
            - Sin comillas, sin punto final, sin prefijos del tipo "Título:".
            - Responde ÚNICAMENTE con el título.

            Mensaje del estudiante:
            {{it}}
            """)
    String titular(String enunciado);
}
