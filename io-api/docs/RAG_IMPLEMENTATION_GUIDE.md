# Guía de implementación RAG — io-api

> Esta guía es para la sesión que va a implementar el RAG.
> Lee también `docs/ARQUITECTURA_IA.md` para entender cómo encaja con la capa de IA existente.

---

## Contexto

El proyecto ya tiene todo lo necesario a nivel de dependencias. No hay que agregar nada
al `build.gradle`. Lo que falta es construir tres piezas en orden.

**Lo que YA existe:**
```groovy
// build.gradle — ya están estas líneas
implementation 'dev.langchain4j:langchain4j-chroma:1.13.0-beta23'
implementation 'dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2-q:1.13.0-beta23'
```

```yaml
# docker-compose.yml — ChromaDB ya está definido
chromadb:
  image: chromadb/chroma
  ports: ["8000:8000"]
```

```yaml
# application.yaml — URL ya configurada
app:
  chroma-url: ${CHROMA_URL:http://localhost:8000}
```

**Lo que falta construir:**
1. El corpus (archivos `.md` con teoría de IO)
2. La infraestructura de ingesta (leer corpus → chunking → embeddings → Chroma)
3. El `ContentRetriever` conectado al `TutorAiService`

---

## Pieza 1 — El corpus

### Dónde va
```
io-api/src/main/resources/corpus/
└── lp/
    ├── 01_que_es_programacion_lineal.md
    ├── 02_como_formular_un_modelo_lp.md
    ├── 03_metodo_simplex_teoria.md
    ├── 04_interpretacion_de_resultados.md
    ├── 05_casos_especiales.md
    └── 06_errores_comunes_al_modelar.md
```

Cuando se implementen otros módulos, agregar carpetas hermanas:
```
corpus/
├── lp/
├── transporte/
├── redes/
├── entera/
├── dinamica/
└── inventarios/
```

### Qué debe contener cada archivo

El contenido tiene que estar escrito pensando en **cómo va a usarlo el LLM**,
no como un manual formal. Fragmentos cortos, autocontenidos, con ejemplos concretos.

| Archivo | Contenido sugerido |
|---|---|
| `01_que_es_programacion_lineal.md` | Definición, cuándo aplica PL, características que identifican un problema como PL (linealidad, variables continuas, restricciones lineales) |
| `02_como_formular_un_modelo_lp.md` | Paso a paso: identificar variables, construir función objetivo, formular restricciones, condiciones de no negatividad. Ejemplos resueltos de formulación |
| `03_metodo_simplex_teoria.md` | Qué es el tableau, variables de holgura, base factible inicial, regla de Dantzig (variable entrante), razón mínima (variable saliente), operación de pivote, condición de optimalidad |
| `04_interpretacion_de_resultados.md` | Cómo leer la solución óptima, qué significan las variables de holgura en la solución final, interpretación económica de Z* |
| `05_casos_especiales.md` | Infactible (qué lo causa, cómo detectarlo en el tableau), No acotado (qué lo causa), Óptimos múltiples (cómo detectarlos: variable no básica con coeficiente cero en fila z) |
| `06_errores_comunes_al_modelar.md` | Confundir MAX con MIN, invertir coeficientes, olvidar restricciones de no negatividad, usar ≥ cuando Simplex estándar solo admite ≤ |

### Guía de redacción del corpus

- Cada archivo debe poder leerse de forma independiente (sin depender de los otros)
- Usar encabezados `##` para separar subtemas — el chunker los usa como límites naturales
- Incluir al menos un ejemplo numérico concreto por archivo
- Escribir en español (el tutor responde en español)
- Evitar párrafos de más de 8-10 líneas — chunks pequeños recuperan mejor

---

## Pieza 2 — Infraestructura de ingesta

### Nuevo archivo: `infrastructure/ai/rag/RagConfig.java`

```java
package jpap.dev.io_api.infrastructure.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${app.chroma-url}") String chromaUrl) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaUrl)
                .collectionName("io-corpus")
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(4)        // máximo 4 fragmentos por consulta
                .minScore(0.6)        // umbral de similitud (0-1)
                .build();
    }
}
```

> **Nota sobre `EmbeddingModel`:** el starter de LangChain4j debería auto-configurar
> un bean `EmbeddingModel` al detectar la dependencia `langchain4j-embeddings-all-minilm-l6-v2-q`.
> Si no lo hace, declararlo manualmente:
> ```java
> @Bean
> public EmbeddingModel embeddingModel() {
>     return new AllMiniLmL6V2QuantizedEmbeddingModel();
> }
> ```
> Import: `dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel`

---

### Nuevo archivo: `infrastructure/ai/rag/CorpusIngester.java`

```java
package jpap.dev.io_api.infrastructure.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CorpusIngester {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public CorpusIngester(EmbeddingStore<TextSegment> embeddingStore,
                          EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void ingestar() throws IOException {
        // Solo ingesta si la colección está vacía (evita duplicados al reiniciar)
        // Nota: ChromaEmbeddingStore no tiene método size() directo en 1.13.0;
        // usar una variable de entorno o flag en application.yaml para controlar
        // si se re-ingesta: app.rag.reingestar=false

        log.info("[RAG] Iniciando ingesta del corpus...");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] recursos = resolver.getResources("classpath:corpus/**/*.md");

        if (recursos.length == 0) {
            log.warn("[RAG] No se encontraron archivos en classpath:corpus/. Ingesta omitida.");
            return;
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                500,   // tamaño máximo del chunk en caracteres
                50     // solapamiento entre chunks (overlap)
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        for (Resource recurso : recursos) {
            String contenido = recurso.getContentAsString(StandardCharsets.UTF_8);
            Document documento = Document.from(contenido);
            ingestor.ingest(documento);
            log.info("[RAG] Ingestado: {}", recurso.getFilename());
        }

        log.info("[RAG] Ingesta completada — {} archivo(s) procesados.", recursos.length);
    }
}
```

> **Sobre el control de re-ingesta:** la ingesta al arrancar siempre duplicará documentos
> si Chroma ya tiene datos. Opciones:
> - Opción simple: agregar `app.rag.reingestar=true/false` en `application.yaml` y
>   wrappear el `@PostConstruct` con esa condición
> - Opción robusta: antes de ingestar, limpiar la colección con
>   `embeddingStore.removeAll()` (disponible en LangChain4j 1.13.0)

---

## Pieza 3 — Conectar el retriever al tutor

### Modificar `AiConfig.java`

Agregar `ContentRetriever` como parámetro del bean `tutorAiService` y añadirlo al builder:

```java
@Bean
public TutorAiService tutorAiService(ChatModel chatModel,
                                     SimplexTool simplexTool,
                                     SugerirModeloTool sugerirTool,
                                     ValidarModeloTool validarTool,
                                     ContentRetriever contentRetriever) throws IOException {
    String systemPrompt = cargarPrompt("classpath:prompts/tutor_system_prompt.txt");
    return AiServices.builder(TutorAiService.class)
            .chatModel(chatModel)
            .chatMemoryProvider(memId -> MessageWindowChatMemory.withMaxMessages(30))
            .tools(simplexTool, sugerirTool, validarTool)
            .systemMessageProvider(memId -> systemPrompt)
            .contentRetriever(contentRetriever)             // ← única línea nueva
            .build();
}
```

Con esto, **antes de cada turno del chat**, LangChain4j automáticamente:
1. Embede la pregunta del estudiante con AllMiniLM
2. Busca en Chroma los 4 fragmentos más similares (score ≥ 0.6)
3. Los inyecta como contexto adicional al LLM junto al historial

El tutor podrá responder preguntas teóricas usando el corpus sin que hayas cambiado
nada más en la lógica de negocio.

---

## Flujo completo una vez implementado

```
Estudiante: "¿Por qué en el Simplex la variable entrante es la de
             coeficiente más negativo en la fila z?"
    │
    ▼
ContentRetriever embede la pregunta → busca en Chroma
    │  Recupera fragmento de 03_metodo_simplex_teoria.md
    │  (regla de Dantzig, criterio de optimalidad)
    ▼
LLM recibe:
  [system prompt socrático]
  [fragmento recuperado del corpus con la teoría]
  [historial de la sesión]
  [pregunta del estudiante]
    │
    ▼
Tutor responde fundamentado en la teoría del corpus,
no solo en el conocimiento general del LLM
```

---

## Orden de implementación recomendado

```
1. Redactar los archivos .md del corpus (Pieza 1)
   → Sin esto, las Piezas 2 y 3 no tienen valor

2. Arrancar ChromaDB
   docker compose up chromadb -d

3. Implementar RagConfig.java (EmbeddingStore + ContentRetriever)

4. Implementar CorpusIngester.java con control de re-ingesta

5. Modificar AiConfig.java para añadir .contentRetriever(...)

6. Probar con una pregunta teórica en el chat y verificar en los logs
   que [RAG] aparece con los archivos ingestados

7. Ajustar maxResults y minScore según la calidad de las respuestas
```

---

## Consideraciones para la sesión de implementación

- **ChromaDB debe estar corriendo** antes de arrancar la app. El `docker-compose.yml`
  ya lo tiene definido — solo ejecutar `docker compose up chromadb -d`.

- **La primera vez que arranque**, la ingesta puede tardar 10-30 segundos dependiendo
  del volumen del corpus (AllMiniLM corre local, en CPU).

- **`minScore: 0.6`** es un punto de partida razonable. Si el tutor trae fragmentos
  irrelevantes, súbelo a 0.7. Si no recupera nada útil, bájalo a 0.5.

- **`maxResults: 4`** agrega ~4 fragmentos de ~500 chars = ~2000 chars de contexto extra
  por turno. Con Llama 3.3-70b en Groq esto es manejable, pero si ves que las respuestas
  se vuelven lentas o incoherentes, baja a 2-3.

- **El `ModeloAiService`** (extracción y validación estructurada) deliberadamente NO
  recibe el `ContentRetriever` — no necesita teoría, necesita precisión en el JSON.
  Solo el `TutorAiService` lo usa.

---

## Archivos que tocar en esa sesión

| Acción | Archivo |
|---|---|
| Crear | `src/main/resources/corpus/lp/*.md` (el corpus) |
| Crear | `infrastructure/ai/rag/RagConfig.java` |
| Crear | `infrastructure/ai/rag/CorpusIngester.java` |
| Modificar | `infrastructure/ai/AiConfig.java` (añadir `.contentRetriever(...)`) |
| Verificar | `application.yaml` — que `app.chroma-url` apunte al Chroma correcto |
