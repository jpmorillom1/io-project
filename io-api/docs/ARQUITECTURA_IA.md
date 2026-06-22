# Arquitectura de la capa de IA

## Estado actual

| Responsabilidad | Archivo | Estado |
|---|---|---|
| Comportarse | `resources/prompts/tutor_system_prompt.txt` | ✅ Implementado |
| Hacer — resolver | `infrastructure/ai/tools/SimplexTool.java` | ✅ Implementado |
| Hacer — sugerir modelo | `infrastructure/ai/tools/SugerirModeloTool.java` | ✅ Implementado |
| Hacer — validar modelo | `infrastructure/ai/tools/ValidarModeloTool.java` | ✅ Implementado |
| Bus de datos tools→controller | `infrastructure/ai/ChatContextStore.java` | ✅ Implementado |
| Saber (RAG) | `infrastructure/ai/rag/` (RagConfig + CorpusIngester) | ✅ Implementado |
| Registrar interacciones | tabla `interaccion_ia` | ⏳ Pendiente |

---

## El orquestador — TutorAiService

Interfaz conversacional con memoria de sesión. Se construye **manualmente** en `AiConfig`
(no usar `@AiService` del starter — da problemas con la configuración de memoria y tools).

```java
// infrastructure/ai/TutorAiService.java
public interface TutorAiService {
    String chat(@MemoryId String sesionId, @UserMessage String mensaje);
}

// infrastructure/ai/AiConfig.java  — bean manual
@Bean
public TutorAiService tutorAiService(ChatModel chatModel, SimplexTool tool) throws IOException {
    String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
    return AiServices.builder(TutorAiService.class)
            .chatModel(chatModel)                                           // ← ChatModel, no ChatLanguageModel
            .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(30))
            .tools(tool)
            .systemMessageProvider(memId -> systemPrompt)                  // ← carga desde archivo
            .build();
}
```

### Memoria de sesión

- **Implementación actual:** `MessageWindowChatMemory.withMaxMessages(30)` en RAM
- **Clave:** el `sesionId` (UUID) que el cliente envía en cada request
- **Límite:** 30 mensajes por sesión (ventana deslizante)
- **Pérdida:** al reiniciar el servidor se pierden todas las sesiones

**Para persistir en PostgreSQL** (pendiente): implementar `ChatMemoryStore` respaldado
por la tabla `sesion` + `interaccion_ia`. El cliente no cambia — sigue enviando el mismo
`sesionId` y LangChain4j carga/guarda automáticamente.

---

## Hacer — @Tool y ChatContextStore

El chat es el **único entrypoint** de la capa de IA. Cuando el tutor decide sugerir
un modelo, validarlo o resolverlo, invoca la tool correspondiente. Las tools escriben
datos estructurados en `ChatContextStore` (ThreadLocal) y el controlador los lee al
terminar, incluyéndolos en `ChatResponse`.

### Las tres tools

| Tool | Cuándo la invoca el tutor | Escribe en el store |
|---|---|---|
| `SugerirModeloTool.registrarModeloSugerido` | Cuando identifica el modelo completo del enunciado | `modeloSugerido: ModeloLP` |
| `ValidarModeloTool.registrarValidacion` | Cuando evalúa el modelo del estudiante | `validacion: ValidacionResponse` |
| `SimplexTool.resolverSimplex` | Solo cuando modelo validado y estudiante pide resolver | `resultado: SolveResult<SolucionLP>` |

El tutor puede **encadenar tools** en una misma respuesta. Ejemplo: al validar un modelo
con errores puede llamar `registrarValidacion` + `registrarModeloSugerido` (con la versión
corregida) en el mismo turno.

### ChatContextStore

```java
// infrastructure/ai/ChatContextStore.java
@Component
public class ChatContextStore {
    private final ThreadLocal<DatosRespuesta> local = new ThreadLocal<>();

    public void iniciar() { local.set(new DatosRespuesta()); }
    public DatosRespuesta obtener() { ... }
    public void limpiar() { local.remove(); }   // siempre en finally

    public static class DatosRespuesta {
        public ModeloLP modeloSugerido;
        public ValidacionResponse validacion;
        public SolveResult<SolucionLP> resultado;
    }
}
```

Funciona con ThreadLocal porque LangChain4j en modo síncrono ejecuta tools en el mismo
hilo que `chat()`. El controlador siempre limpia en `finally`.

### Flujo completo de una llamada

```
POST /api/v1/ai/chat
    │
    ▼
AiChatController: contextStore.iniciar()
    │
    ▼
TutorAiService.chat(sesionId, mensaje)
    │  LLM decide qué tools invocar (0, 1 o varias)
    │
    ├── registrarModeloSugerido(vars, coefs, tipo, restricciones)
    │       → contextStore.modeloSugerido = ModeloLP
    │
    ├── registrarValidacion(esValido, analisis, errores, sugerencias)
    │       → contextStore.validacion = ValidacionResponse
    │
    └── resolverSimplex(vars, coefs, tipo, restricciones)
            → SimplexUseCase → SimplexSolver (dominio puro)
            → contextStore.resultado = SolveResult<SolucionLP>
            → devuelve String con detalle de iteraciones al LLM
    │
    ▼
LLM genera respuesta pedagógica usando los resultados de las tools
    │
    ▼
AiChatController lee contextStore, construye ChatResponse enriquecido:
{
  sesionId, respuesta,
  modeloSugerido (nullable),
  validacion (nullable),
  resultado (nullable)
}
    │
    ▼
contextStore.limpiar()  [en finally]
    │
    ▼
Frontend actualiza formulario y/o tableau automáticamente
```

### SimplexTool — RestriccionInput y @JsonIgnoreProperties

`SimplexTool.RestriccionInput` no tiene campo `tipo` (el solver solo acepta LEQ).
`SugerirModeloTool.RestriccionInput` sí lo tiene. Como el LLM aprende los schemas de
todas las tools, puede enviar `tipo` también al llamar `resolverSimplex`. La anotación
`@JsonIgnoreProperties(ignoreUnknown = true)` en el record evita el error de Jackson.

---

## Structured Output — ModeloAiService

Para extracción y validación de modelos sin memoria. LangChain4j genera automáticamente
instrucciones JSON a partir del tipo de retorno del método.

```java
// infrastructure/ai/ModeloAiService.java
public interface ModeloAiService {

    @SystemMessage("Eres un extractor de modelos de PL...")
    ModeloSugeridoResponse extraerModelo(@UserMessage String descripcion);

    @SystemMessage("Eres un validador de modelos de PL...")
    ValidacionResponse validarModelo(@UserMessage String descripcionYModelo);
}
```

El método puede retornar un record anidado complejo (`ModeloSugeridoResponse` contiene
`ModeloLP` que contiene `FuncionObjetivo` y `List<Restriccion>`). LangChain4j genera
el schema JSON y el LLM (Llama 3.3) lo sigue sin necesidad de configuración extra.

---

## Comportarse — System Prompt

Vive en `resources/prompts/tutor_system_prompt.txt`. Se carga en runtime (no compilado),
por lo que puedes editarlo sin reiniciar el servidor.

### Principio central del prompt

El prompt es **adaptativo**: antes de responder, la IA evalúa qué información ya
proporcionó el estudiante y solo pregunta por lo que falta.

- Si el estudiante da todo de una vez → va directo a validar
- Si da algo parcial → confirma lo que está bien, pregunta solo por lo que falta
- Si solo hay enunciado → guía desde el principio con preguntas socráticas

### Reglas inamovibles del prompt

- No resolver sin modelo validado y petición explícita
- No encadenar extracción → resolución sin intervención del estudiante
- No preguntar por información que ya fue dada
- No corregir directamente: usar preguntas para que el estudiante encuentre el error

---

## Saber — RAG ✅ IMPLEMENTADO

El tutor accede a un corpus de teoría mediante Retrieval Augmented Generation (RAG).

### Componentes

**`RagConfig.java`** — beans de RAG:
```java
@Bean
public EmbeddingModel embeddingModel() {
    return new AllMiniLmL6V2QuantizedEmbeddingModel();  // local, ~100MB
}

@Bean
public EmbeddingStore<TextSegment> embeddingStore(@Value("${app.chroma-url}") String chromaUrl) {
    return ChromaEmbeddingStore.builder()
            .apiVersion(ChromaApiVersion.V2)  // ← CRÍTICO: Chroma v2 API
            .baseUrl(chromaUrl)
            .collectionName("io-corpus")
            .build();
}

@Bean
public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store,
                                         EmbeddingModel embeddingModel) {
    return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .maxResults(6)      // 4 resultados era poco, 6 es mejor
            .minScore(0.5)      // 0.6 era muy estricto para AllMiniLM
            .build();
}
```

**`CorpusIngester.java`** — carga automática del corpus en ChromaDB:
```java
@PostConstruct
public void ingestar() throws IOException {
    if (!reingestar) { log.info("[RAG] re-ingesta omitida"); return; }
    
    embeddingStore.removeAll();  // limpiar para evitar duplicados
    
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] recursos = resolver.getResources("classpath:corpus/**/*.md");
    
    DocumentSplitter splitter = DocumentSplitters.recursive(350, 30);
    EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(splitter)
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();
    
    for (Resource recurso : recursos) {
        ingestor.ingest(Document.from(recurso.getContentAsString()));
    }
}
```

**Integración en `AiConfig.java`**:
```java
@Bean
public TutorAiService tutorAiService(..., ContentRetriever contentRetriever) {
    return AiServices.builder(TutorAiService.class)
            ...
            .contentRetriever(contentRetriever)  // ← injecta fragmentos automáticamente
            .build();
}
```

### Corpus

6 archivos markdown en `resources/corpus/lp/`:
1. **01_que_es_programacion_lineal.md** — qué es PL, cuándo aplica, propiedades formales
2. **02_como_formular_un_modelo_lp.md** — paso a paso de formulación, ejemplos
3. **03_metodo_simplex_teoria.md** — tableau, Dantzig, razón mínima, pivote
4. **04_interpretacion_de_resultados.md** — lectura de solución, holguras (sᵢ=0 vs >0), precios sombra
5. **05_casos_especiales.md** — infactible, no acotado, óptimos múltiples
6. **06_errores_comunes_al_modelar.md** — 6 errores de formulación comunes

Total: **~60 chunks** de 350 chars con overlap de 30.

### Flujo en cada turno del chat

```
Estudiante: "¿Qué es una variable de holgura?"
    │
    ▼
LangChain4j embede la pregunta con AllMiniLM
    │
    ▼
Busca en ChromaDB: 6 fragmentos más similares (score ≥ 0.5)
    │  Recupera: chunk de 04_interpretacion_de_resultados.md
    │
    ▼
Inyecta automáticamente en el prompt:
"Answer using the following information:
## Significado de las variables de holgura...
sᵢ = 0 significa recurso completamente agotado...
sᵢ > 0 significa capacidad sobrante..."
    │
    ▼
LLM (Llama 3.3-70b) genera respuesta usando el contexto RAG
    │
    ▼
Tutor responde fundamentado en la teoría, no inventando
```

### Parámetros críticos

- **Chunking**: 350 chars + 30 de overlap → cada sección `##` queda autocontenida
- **Retrieval**: 6 fragmentos, score ≥ 0.5 (balance entre relevancia y cobertura)
- **Re-ingesta**: `app.rag.reingestar` en `application.yaml` (default false)
  - Si modificas los `.md`: arranca con `RAG_REINGESTAR=true` una vez
  
### Notas importantes

- **ChromaDB v2 API es obligatorio** — LangChain4j 1.13.0-beta23 no soporta v1
- **AllMiniLM corre local en CPU** — no requiere API key, no es una dependencia externa
- **El `ModeloAiService` NO recibe ContentRetriever** — solo el `TutorAiService` lo usa
- **Exclusión en build.gradle**: `exclude group: 'dev.langchain4j', module: 'langchain4j-http-client-jdk'`
  (evita conflicto con Spring RestClient)

---

## Registro de interacciones (pendiente)

La tabla `interaccion_ia` ya existe en la BD. Falta el `@Repository` JPA y el adaptador
que persista cada turno del chat (herramienta, prompt, respuesta, tool invocada, etc.).
Esto alimenta el anexo de prompts que exige el proyecto académico.
