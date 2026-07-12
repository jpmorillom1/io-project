package jpap.dev.io_api.infrastructure.ai.dto;

import tools.jackson.databind.JsonNode;

/**
 * Último problema que la sesión resolvió, para repoblar el workspace al reabrirla.
 *
 * `metodo` es el nombre de un MetodoResolucion (SIMPLEX, GRAFICO, TRANSPORTE, …): le dice
 * al frontend a qué editor va el modelo y a qué panel va el resultado.
 *
 * Modelo y resultado viajan como JsonNode, no como String: en la BD son columnas JSONB y
 * serializarlos como texto le entregaría al cliente un JSON escapado dentro de un string.
 *
 * El JsonNode DEBE ser el de Jackson 3 (tools.jackson): Spring Boot 4 serializa las respuestas
 * con Jackson 3, y el JsonNode de Jackson 2 no lo reconoce como árbol — lo volcaría como un POJO
 * cualquiera, con sus getters (array, nodeType, …) en vez del JSON.
 */
public record ProblemaResueltoHistorial(
        String metodo,
        JsonNode modelo,
        JsonNode resultado
) {}
