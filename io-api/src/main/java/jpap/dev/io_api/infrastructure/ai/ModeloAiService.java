package jpap.dev.io_api.infrastructure.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jpap.dev.io_api.infrastructure.ai.dto.ModeloSugeridoResponse;
import jpap.dev.io_api.infrastructure.ai.dto.ValidacionResponse;

/**
 * Servicio de IA sin memoria para operaciones estructuradas (extracción y validación de modelos).
 * Retorna tipos Java — LangChain4j aplica PojoOutputParser y genera instrucciones JSON automáticamente.
 */
public interface ModeloAiService {

    @SystemMessage("""
            Eres un extractor experto de modelos de Programación Lineal (PL).
            Analiza la descripción del problema y extrae el modelo matemático completo.

            REGLAS ESTRICTAS:
            - Variables: usa nombres como x1, x2, ... a menos que el enunciado los especifique
            - Tipo de restricción: usa LEQ para ≤, GEQ para ≥, EQ para =
            - tipoObjetivo: usa MAXIMIZAR o MINIMIZAR
            - Para Simplex estándar, convierte ≥ a LEQ si es posible multiplicando por -1 en ambos lados
            - Si hay ambigüedad, documéntala en supuestosAplicados
            - Si el texto no describe un PL, pon advertencias y modelo=null

            Responde con el objeto JSON que representa ModeloSugeridoResponse.
            """)
    ModeloSugeridoResponse extraerModelo(@UserMessage String descripcionProblema);

    @SystemMessage("""
            Eres un validador experto de modelos de Programación Lineal.
            Se te dará la descripción original del problema y el modelo propuesto por el estudiante.

            VERIFICA:
            1. Variables: ¿están bien definidas? ¿sus coeficientes coinciden con el enunciado?
            2. Función objetivo: ¿tipo correcto (MAX/MIN)? ¿coeficientes coherentes?
            3. Restricciones: ¿están todas? ¿tipos correctos? ¿b >= 0 para Simplex estándar?
            4. Dimensiones: ¿número de coeficientes = número de variables en cada restricción?

            Si el modelo es correcto: esValido=true, erroresEncontrados=[], modeloCorregido=null
            Si hay errores: esValido=false, lista los errores y proporciona modeloCorregido con la versión corregida

            Responde con el objeto JSON que representa ValidacionResponse.
            """)
    ValidacionResponse validarModelo(@UserMessage String descripcionYModelo);
}
