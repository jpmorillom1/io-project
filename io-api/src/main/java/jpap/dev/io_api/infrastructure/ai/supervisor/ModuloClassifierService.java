package jpap.dev.io_api.infrastructure.ai.supervisor;

import dev.langchain4j.service.UserMessage;

/**
 * Clasificador LLM para determinar con precisión el módulo de IO al que pertenece
 * el mensaje del usuario, evitando falsos positivos por palabras clave.
 */
public interface ModuloClassifierService {

    enum ModuloDetectado {
        PL,
        INVENTARIO,
        TRANSPORTE,
        REDES,
        ENTERA,
        DINAMICA,
        GENERAL,
        CONTINUAR
    }

    @UserMessage("""
            Analiza el siguiente mensaje de un estudiante de Investigación Operativa y clasifícalo en UNA de las siguientes categorías:

            - PL: Programación Lineal Continua (maximizar o minimizar utilidades/costos sujetos a restricciones de recursos, producción, horas, mezcla de productos, Simplex, Gráfico).
            - DINAMICA: Programación Dinámica por etapas o periodos (ecuación de Bellman, planificación de producción por meses/periodos, problema de la mochila, asignación de recursos, ruta por etapas, reemplazo de equipos). REGLA CRÍTICA: SI EL MENSAJE MENCIONA "Programación Dinámica" O PLANIFICACIÓN DE PRODUCCIÓN POR PERIODOS/MESES, CLASIFÍCALO SIEMPRE COMO DINAMICA, AUNQUE MENCIONE COSTOS DE INVENTARIO O ALMACENAMIENTO.
            - INVENTARIO: Gestión de inventarios estáticos con demanda constante (EOQ básico, lote económico de compra Q*, punto de reorden ROP, descuentos por volumen de compra). NO lo uses si el problema menciona Programación Dinámica o demandas variables por periodo.
            - TRANSPORTE: Problemas de transporte u oferta/demanda entre orígenes y destinos.
            - REDES: Problemas sobre grafos (ruta más corta, árbol de expansión mínima, flujo máximo).
            - ENTERA: Programación Lineal Entera o Binaria (cuando se enfatiza que las variables deben ser enteras o binarias por ramificación).
            - GENERAL: Saludos ("hola", "buenas"), preguntas sobre qué es la plataforma o qué puede hacer el tutor, preguntas TEÓRICAS o conceptuales de Investigación Operativa que NO estén ligadas a un problema propio del estudiante con datos concretos a modelar (ej. "¿qué es la Investigación Operativa?", "¿cuál es la diferencia entre Simplex y Gran M?"), y cualquier mensaje que no sea de Investigación Operativa en absoluto. NO uses GENERAL si el mensaje ya trae datos de un problema real (variables, función objetivo, restricciones, orígenes/destinos, nodos, etc.): en ese caso clasifica por el módulo que corresponda.
            - CONTINUAR: Si el mensaje es corto (ej. "sí", "resuélvelo", "ok", "está bien", "¿por qué?") o una continuación directa de la conversación actual sin cambiar de problema ni de tema.

            Mensaje del estudiante:
            {{it}}
            """)
    ModuloDetectado clasificar(String mensaje);
}
