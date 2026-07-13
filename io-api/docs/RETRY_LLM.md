# Retry ante `tool_use_failed` — tolerancia a tool calls malformadas del LLM

> Implementado en julio 2026. Piezas: `RetryingChatModel` (nuevo), `AiConfig` y
> `AiChatController` (modificados).

---

## 1. El problema

De forma intermitente, al enviar un mensaje al chat el estudiante veía esto:

```
Error interno: 400 Bad Request: "{"error":{"message":"Failed to call a function.
Please adjust your prompt. See 'failed_generation' for more details.",
"type":"invalid_request_error","code":"tool_use_failed",
"failed_generation":"<function=resolverSimplex,{\"objetivoCoeficientes\":[5,9],...}</function>"}}"
```

### Causa raíz

El error **no es de nuestro backend ni del prompt**: es el propio LLM
(`llama-3.3-70b-versatile` en Groq) generando la llamada a la tool con **sintaxis
malformada**. En el ejemplo, emitió `<function=resolverSimplex,{...}</function>`
(una coma donde debía cerrar la etiqueta con `>`). Groq intenta parsear esa
generación para convertirla en una tool call estructurada, no puede, y responde
**HTTP 400 con `code: tool_use_failed`** — el campo `failed_generation` trae el
texto crudo que el modelo produjo.

La cadena de propagación era:

```
Groq 400 tool_use_failed
  → LangChain4j lo mapea a InvalidRequestException
  → InvalidRequestException extiende NonRetriableException  ← por eso LangChain4j NO reintenta
  → la excepción sube por AiServices hasta AiChatController
  → GlobalExceptionHandler la envuelve: "Error interno: ..." (HTTP 500)
  → el frontend la muestra tal cual en el chat
```

Dos agravantes:

1. **LangChain4j no cubre este caso.** Su retry interno solo aplica a errores
   clasificados como reintentables (429, 5xx, timeouts). Un 400 es "culpa del
   cliente" → `NonRetriableException` → cero reintentos. Pero aquí el 400 no es
   culpa de nuestro request: es la generación del modelo, que **sí** cambia entre
   intentos.
2. **`temperature: 0.0`** (nuestra configuración en `application.yaml`) hace la
   generación casi determinista: reintentar el request idéntico tendería a
   producir la misma tool call malformada.

---

## 2. La solución — dos capas

**Regla de diseño:** el error crudo del proveedor LLM jamás debe llegar al chat.
Capa 1 lo resuelve casi siempre; capa 2 garantiza degradación con gracia si no.

### Capa 1 — `RetryingChatModel`: reintento a nivel de modelo

`infrastructure/ai/RetryingChatModel.java` es un **decorador** de `ChatModel`
(patrón wrapper — en LangChain4j 1.13.0 todos los métodos de la interfaz son
`default`, así que basta implementar la interfaz y delegar):

```
AiServices (tutor / modelo)
    └── RetryingChatModel          ← reintenta hasta 3 veces si tool_use_failed
            └── OpenAiChatModel    ← auto-configurado por el starter (Groq)
```

Comportamiento de `chat(ChatRequest)`:

| Intento | Request | Temperatura |
|---|---|---|
| 1 | original | 0.0 (la configurada) |
| 2 | mismos mensajes | **0.4** |
| 3 | mismos mensajes | **0.4** |

- Solo reintenta si la excepción (o alguna de sus causas) contiene
  `tool_use_failed`, `failed_generation` o `Failed to call a function`.
  Cualquier otro error (API key inválida, rate limit, timeout) se propaga
  intacto — no es nuestro caso y enmascararlo dificultaría el diagnóstico.
- Los reintentos suben la temperatura a **0.4** reconstruyendo el request con
  `request.parameters().overrideWith(...)`. Sin esto, con `temperature=0` el
  modelo regeneraría casi la misma salida malformada las tres veces y el retry
  sería inútil. El resto de parámetros (modelo, max-tokens, tools, response
  format) se conserva.
- Si los 3 intentos fallan, relanza la última excepción (la captura la capa 2).

#### Por qué reintentar a nivel de `ChatModel` y no re-llamando al AiService

Es la decisión más importante del diseño. Dentro de un turno,
`tutorAiService.chat(...)` ejecuta un **bucle**: llamada al modelo → ejecución
de tools → nueva llamada al modelo → ... El fallo ocurre en *una* llamada HTTP
de ese bucle. Reintentar ahí adentro significa que:

- **La memoria de sesión no se corrompe.** Re-llamar al AiService completo
  volvería a añadir el mensaje del usuario a la `ChatMemory` (quedaría duplicado).
- **Las tools ya ejecutadas del turno no se re-ejecutan.** Si el fallo ocurre en
  la segunda llamada al modelo (después de que una tool ya corrió y escribió en
  `ChatContextStore`), un retry externo repetiría esa tool — con HITL, crearía
  una segunda `SolicitudAprobacion`.
- El retry es transparente para `AiServices`, el `ToolService` y el RAG: solo
  se repite la llamada HTTP exacta que falló.

#### Dónde se enchufa

En `AiConfig`, ambos services reciben el modelo envuelto:

```java
return AiServices.builder(TutorAiService.class)
        .chatModel(new RetryingChatModel(chatModel))   // ← antes: chatModel a secas
        ...
```

Igual para `ModeloAiService`, así que el retry también cubre
`/sugerir-modelo` y `/validar-modelo` (usan structured output; menos propensos
al fallo, pero cubiertos). El workflow HITL no invoca al `ChatModel`
directamente, no necesita cambios.

### Capa 2 — Fallback en `AiChatController`: mensaje amable

Si los 3 intentos se agotan (o el LLM falla por cualquier otra razón), los
endpoints `/chat` y `/chat/aprobacion` capturan la `RuntimeException` y
devuelven un `ChatResponse` **normal (HTTP 200)** con un mensaje en español:

- **`/chat`:**
  > "Lo siento, tuve un problema técnico al procesar tu mensaje. Por favor,
  > envíalo de nuevo — la conversación sigue guardada."

- **`/chat/aprobacion`:** caso especial — cuando el estudiante aprueba, el
  solver **ya corrió** antes de que el tutor genere la explicación. Si el LLM
  falla en ese punto, el `ChatResponse` **igual lleva `resultado` /
  `resultadoGrafico`** para que la UI muestre el procedimiento, con el mensaje:
  > "El método se ejecutó correctamente y el procedimiento ya está en pantalla,
  > pero tuve un problema técnico al preparar la explicación. Pídeme que te
  > explique el resultado y lo retomamos."

Al ser HTTP 200, el frontend lo trata como un mensaje más del tutor: no se
dispara el manejo de error de `useChat.ts` (que además resetea la sesión) y la
conversación sigue viva. La excepción real queda en el log del servidor con
stack trace completo.

---

## 3. Archivos tocados

| Archivo | Cambio |
|---|---|
| `infrastructure/ai/RetryingChatModel.java` | **Nuevo.** Decorador con el retry (3 intentos, temperatura 0.4 en reintentos) |
| `infrastructure/ai/AiConfig.java` | Envuelve el `ChatModel` con `RetryingChatModel` en ambos beans |
| `infrastructure/ai/AiChatController.java` | `catch (RuntimeException)` en `/chat` y `/chat/aprobacion` + constantes `MENSAJE_ERROR_LLM*` |

Frontend: **sin cambios** — la degradación llega como respuesta normal del chat.

---

## 4. Observabilidad

Trazas en el log del backend:

```
WARN  [AI/retry] intento 1/3 falló por tool call malformada del LLM: 400 Bad Request: ...
WARN  [AI/retry] intento 2/3 falló por tool call malformada del LLM: ...
ERROR [AI/retry] agotados 3 intentos por tool call malformada
ERROR [AI/chat] fallo del LLM tras reintentos — sesionId=... (con stack trace)
```

Si en el log aparece un solo `WARN [AI/retry]` seguido de un turno normal, el
retry funcionó y el estudiante no notó nada — que es el caso esperado la
inmensa mayoría de las veces.

---

## 5. Límites conocidos y evolución

- La detección del error es por **substring del mensaje** (`tool_use_failed`,
  `failed_generation`). Si LangChain4j o Groq cambian el formato del error,
  revisar `RetryingChatModel.esToolCallMalformada(...)`.
- `MAX_INTENTOS = 3` y `TEMPERATURA_REINTENTO = 0.4` son constantes de la clase.
  Si se quisieran configurables, moverlas a `application.yaml` bajo `app.`.
- El retry es secuencial y sin backoff: correcto aquí porque el 400 responde
  inmediato (no hay rate limit de por medio). No añadir sleep salvo evidencia.
- Si algún día se cambia de Groq/llama a un proveedor con tool calling nativo
  más robusto (p. ej. modelos de Anthropic u OpenAI), este wrapper se vuelve
  prácticamente inerte — no estorba, solo no se activa.
