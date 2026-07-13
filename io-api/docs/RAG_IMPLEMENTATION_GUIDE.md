# Guía de implementación RAG — io-api

> **STATUS: COMPLETADO  (con soporte PDF)** — Esta guía documenta la implementación del RAG realizada.
> Para más contexto, lee `CLAUDE.md` sección 7 y `docs/ARQUITECTURA_IA.md`.

---

## Contexto

El proyecto tiene todo lo necesario a nivel de dependencias para soportar Markdown y PDF.

**Dependencias LangChain4j activas en `build.gradle`:**
```groovy
implementation 'dev.langchain4j:langchain4j-spring-boot4-starter:1.13.0-beta23'
implementation 'dev.langchain4j:langchain4j-open-ai-spring-boot4-starter:1.13.0-beta23'
implementation('dev.langchain4j:langchain4j-chroma:1.13.0-beta23') {
    exclude group: 'dev.langchain4j', module: 'langchain4j-http-client-jdk'
}
implementation 'dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2-q:1.13.0-beta23'
implementation 'dev.langchain4j:langchain4j-document-parser-apache-pdfbox:1.13.0-beta23'
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
├── investigacion-de-operaciones-taha-hamdy-2004.pdf   ← libro general (todos los módulos)
└── lp/
    ├── 01_que_es_programacion_lineal.md
    ├── 02_como_formular_un_modelo_lp.md
    ├── 03_metodo_simplex_teoria.md
    ├── 04_interpretacion_de_resultados.md
    ├── 05_casos_especiales.md
    └── 06_errores_comunes_al_modelar.md
```

**Regla de ubicación**: los libros PDF van en la raíz de `corpus/` (no en subcarpetas de módulo)
porque cubren todos los temas. Los `.md` de teoría van en la subcarpeta del módulo al que pertenecen.

Cuando se implementen otros módulos, agregar carpetas hermanas para sus notas `.md`:
```
corpus/
├── investigacion-de-operaciones-taha-hamdy-2004.pdf   ← ya existe
├── lp/          ← ya existe
├── transporte/  ← agregar cuando se implemente el módulo
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

### `infrastructure/ai/rag/CorpusIngester.java` (implementación actual)

Maneja dos fuentes de forma independiente con chunk sizes distintos:

| Fuente | Glob | Chunk | Overlap | Parser |
|--------|------|-------|---------|--------|
| `.md` notas de teoría | `corpus/**/*.md` | 350 | 30 | `Document.from(String)` |
| `.pdf` libro de texto | `corpus/**/*.pdf` | 700 | 70 | `ApachePdfBoxDocumentParser` |

El toggle `app.rag.incluir-pdf` (env `RAG_INCLUIR_PDF`, default `true`) permite omitir el
PDF para re-ingestas rápidas cuando solo cambiaron los `.md`. Ambas fuentes van a la misma
colección `io-corpus`; `removeAll()` limpia todo antes de re-ingestar.

**Guard contra PDF escaneado**: si PDFBox extrae menos de ~10k caracteres, el PDF es
probablemente un escaneo sin capa de texto y no aportará contenido al RAG. Ver log:
```
[RAG] PDF extraído: X caracteres — investigacion-de-operaciones-taha-hamdy-2004.pdf
[RAG] Muestra: ...  (primeros 300 chars para verificar legibilidad, nivel DEBUG)
```

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

## Status actual de la implementación 

### Completado
-  Corpus de 6 archivos `.md` en `src/main/resources/corpus/lp/`
-  Libro Taha "Investigación de Operaciones" (200 págs) en `src/main/resources/corpus/`
-  `RagConfig.java` con EmbeddingModel + EmbeddingStore + ContentRetriever
-  `CorpusIngester.java` con soporte dual MD + PDF y chunking diferenciado
-  `langchain4j-document-parser-apache-pdfbox:1.13.0-beta23` añadida a `build.gradle`
-  Toggle `app.rag.incluir-pdf` (`RAG_INCLUIR_PDF`) en `application.yaml`
-  `AiConfig.java` inyecta ContentRetriever en TutorAiService
-  Exclusión de `langchain4j-http-client-jdk` en `build.gradle` para evitar conflictos HTTP
-  ChromaDB API v2 configurado correctamente (`.apiVersion(ChromaApiVersion.V2)`)

### Parámetros de chunking (ya configurados)
| Fuente | Chunk size | Overlap | Justificación |
|--------|-----------|---------|---------------|
| `.md` teoría | 350 chars | 30 | Secciones `##` cortas, autocontenidas |
| `.pdf` libro | 700 chars | 70 | Prosa densa; ~140 tokens (límite AllMiniLM = 256) |

### Parámetros de retrieval (ya configurados)
- Máximo **6 fragmentos** por consulta, score mínimo **0.5**

### Notas operacionales

- **ChromaDB debe estar corriendo** antes de arrancar la app:
  ```bash
  docker compose up chromadb -d
  ```

- **Flujos de re-ingesta**:

  | Comando | Acción | Tiempo aprox. | Cuándo |
  |---------|--------|---------------|--------|
  | `RAG_REINGESTAR=true RAG_INCLUIR_PDF=true` | MD + PDF | ~2-4 min | Setup inicial, libro cambió |
  | `RAG_REINGESTAR=true RAG_INCLUIR_PDF=false` | Solo MD | ~10 s | Editaste archivos `.md` |
  | `RAG_REINGESTAR=false` (default) | Nada | — | Arranque normal |

- **Verificar extracción del PDF** — revisar en logs al arrancar con `RAG_REINGESTAR=true`:
  - `[RAG] PDF extraído: X caracteres` → si X > 50k, el PDF tiene texto extraíble
  - Si X < 10k → es un escaneo; PDFBox no puede extraer texto sin OCR

- **El `ModeloAiService`** deliberadamente NO recibe el `ContentRetriever` — solo el
  `TutorAiService` lo usa.

---

## Historial de cambios

| Acción | Archivo | Estado |
|---|---|---|
| Crear | `corpus/lp/01_*.md` a `06_*.md` (6 archivos) |  DONE |
| Crear | `infrastructure/ai/rag/RagConfig.java` |  DONE |
| Crear | `infrastructure/ai/rag/CorpusIngester.java` |  DONE |
| Modificar | `infrastructure/ai/AiConfig.java` (`.contentRetriever(...)`) |  DONE |
| Modificar | `build.gradle` (exclude `langchain4j-http-client-jdk`) |  DONE |
| Modificar | `build.gradle` (añadir `langchain4j-document-parser-apache-pdfbox`) |  DONE |
| Modificar | `application.yaml` (añadir `app.rag.incluir-pdf`) |  DONE |
| Modificar | `CorpusIngester.java` (soporte PDF + chunking diferenciado) |  DONE |
| Añadir | `corpus/investigacion-de-operaciones-taha-hamdy-2004.pdf` (libro Taha) |  DONE |
| Corregir | `corpus/lp/04_*.md` — sección de holgura (sᵢ=0 vs sᵢ>0) |  DONE |
| Corregir | `corpus/lp/06_*.md` — estructura para mejor recuperación |  DONE |
