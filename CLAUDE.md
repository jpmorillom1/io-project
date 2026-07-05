# CLAUDE.md — Plataforma genérica de Investigación Operativa con tutoría IA

> Este archivo es el contexto raíz que Claude Code lee automáticamente al iniciar.
> Mantenlo actualizado. Las reglas aquí son **vinculantes** para cualquier cambio de código.

---

## 1. Qué es este proyecto

Plataforma web **genérica** que resuelve problemas de Investigación Operativa (IO) de
cualquier instancia ingresada por el usuario, acompañada de un **tutor socrático asistido
por IA** que orienta el razonamiento sin entregar la respuesta final de primeras.

No es una app atada a un caso: el usuario define los datos de su propio problema mediante
un panel de configuración, y el sistema lo resuelve mostrando el **procedimiento paso a paso**.

### Los seis módulos de IO

| Módulo | Estado | Algoritmos |
|---|---|---|
| Programación Lineal (LP) | **Simplex, Gran M, Dos Fases, Gráfico IMPLEMENTADOS** | Simplex (≤); Gran M, Dos Fases (≤/≥/=); Gráfico (2 variables); análisis post-óptimo incluido. Dual — pendiente |
| Transporte | **Esquina Noroeste, Costo Mínimo, Vogel, MODI IMPLEMENTADOS** | Soluciones iniciales (NW/CostoMin/Vogel) + MODI (óptimo, compara las 3 iniciales); balanceo automático. Húngaro (asignación) — pendiente |
| Redes | TODO estructurado | Dijkstra, Kruskal (+Union-Find), Edmonds-Karp |
| PL Entera | TODO estructurado | Branch & Bound sobre Simplex; Gomory (opcional) |
| Programación Dinámica | TODO estructurado | Tipos parametrizables (asignación/mochila/ruta por etapas) |
| Inventarios | TODO estructurado | EOQ básico, con faltantes, con descuentos, POQ, punto de reorden |

**"TODO estructurado"** = la carcasa existe (interfaz, contrato I/O, formulario), pero el
algoritmo se implementa cuando el docente imparta la teoría. NO inventes la implementación
de un módulo TODO sin que se te indique explícitamente.

---

## 2. Stack tecnológico

- **Backend:** Spring Boot **4.1.0**, Java **21**
- **Frontend:** React + Vite (en `io-ui/`, aún en desarrollo)
- **IA:** LangChain4j **1.13.0** + módulo `langchain4j-agentic` 1.13.0-beta23 para el
  Human-in-the-Loop (ver nota crítica abajo)
- **LLM:** OpenAI-compatible apuntado a **Groq** (`llama-3.3-70b-versatile`)
- **Embeddings:** AllMiniLM-L6-V2 Quantized (local, sin API key)
- **RAG vector store:** ChromaDB v2 API (Chroma 0.6+, **IMPLEMENTADO**)
- **BD relacional:** PostgreSQL 16
- **Build:** Gradle con flag `-parameters` (necesario para Jackson + records)

### ⚠️ Nota crítica de LangChain4j 1.13.0

En esta versión **`ChatLanguageModel` fue renombrado a `ChatModel`**.
- Import correcto: `dev.langchain4j.model.chat.ChatModel`
- Builder correcto: `.chatModel(chatModel)` (no `.chatLanguageModel(...)`)
- No uses la anotación `@AiService` del starter — usamos builder manual en `AiConfig`

> **Identificador del modelo LLM:** NO lo hardcodees. Va en `application.yml`,
> leído de la variable de entorno `GROQ_MODEL_NAME`.

> **API key:** SIEMPRE desde variable de entorno. Nunca en código, nunca commiteada.

---

## 3. Arquitectura (hexagonal / por capas)

```
┌─────────────────────────────────────────────────┐
│  infrastructure  (Spring MVC, JPA, LangChain4j)  │  ← adaptadores
│   ┌─────────────────────────────────────────┐   │
│   │  application  (casos de uso, puertos)    │   │  ← servicios
│   │    ┌───────────────────────────────┐     │   │
│   │    │  domain  (Java puro)          │     │   │  ← núcleo
│   │    └───────────────────────────────┘     │   │
│   └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### Reglas de capa (INVIOLABLES)

1. **`domain`** — Java puro. CERO anotaciones de Spring, CERO imports de LangChain4j,
   CERO imports de `jakarta.persistence`. Solo lógica matemática y records de datos.
2. **`application`** — orquesta. Define interfaces de puerto (`SimplexUseCase`, etc.).
3. **`infrastructure`** — todo lo "sucio": controladores REST, JPA, `@Tool`, `AiConfig`, CORS.

---

## 4. Contrato común de los solvers

```java
// domain/common/SolveResult.java
public record SolveResult<T>(
    SolveStatus status,        // OPTIMO, INFACTIBLE, NO_ACOTADO, MULTIPLE_OPTIMO, ERROR
    T solution,                // null si no hay solución
    List<SolveStep> steps      // iteraciones en orden para mostrar el procedimiento
) {}

public record SolveStep(
    int numero,
    String titulo,
    String descripcion,
    Map<String, Object> datos  // tableau/tabla/grafo del paso para la UI
) {}
```

**Regla:** un solver nunca lanza excepción por infactible o no acotado — son resultados
válidos. Las excepciones se reservan para entradas malformadas.

---

## 5. Lo que está implementado (estado actual)

### domain/common/
- `SolveResult<T>`, `SolveStep`, `SolveStatus` — contrato común de todos los solvers

### domain/lp/
- `ModeloLP`, `FuncionObjetivo`, `Restriccion` — records de entrada inmutables
- `TipoObjetivo` (MAXIMIZAR/MINIMIZAR), `TipoRestriccion` (LEQ/GEQ/EQ)
- `SolucionLP` — record de salida: `valores`, `holguras`, `valorOptimo`, `preciosSombra`, `rangosSensibilidad`
- `RangosSensibilidad`, `RangoCoeficiente`, `RangoRHS` — records del análisis post-óptimo
- `SensibilidadCalculator` — utilidad estática compartida por los tres solvers
- `simplex/SimplexSolver` — Simplex estándar (solo LEQ, b≥0)
- `granm/GranMSolver` — Gran M: LEQ/GEQ/EQ con penalidad M=1.000.000
- `dosfases/DosFasesSolver` — Dos Fases: LEQ/GEQ/EQ
- `grafico/GraficoSolver` — Método gráfico (2 variables); `SolucionGrafica`, `PuntoVertice`

### domain/common/ — contrato compartido
- `ModeloResoluble` — **interfaz marcador** que implementan `ModeloLP` y `ModeloTransporte`;
  permite que la cadena HITL transporte modelos de distintos módulos sin acoplarse a un tipo

### domain/transporte/
- `ModeloTransporte` (record, `implements ModeloResoluble`) — `origenes`, `destinos`, `oferta`,
  `demanda`, `costos` (matriz), `metodo` (`MetodoTransporte`)
- `SolucionTransporte` — `asignaciones` (matriz), `costoTotal`, `comparativaInicial`, `metodoInicial`
- `MetodoTransporte` (enum): ESQUINA_NOROESTE, COSTO_MINIMO, VOGEL, MODI
- `Balanceador` — agrega origen/destino ficticio de costo 0 si Σoferta ≠ Σdemanda; valida dimensiones
- `TransporteUtils` — costo total, snapshots de asignaciones, mapa `datos` de cada paso
- `esquinanoroeste/EsquinaNoroesteSolver`, `costominimo/CostoMinimoSolver`, `vogel/VogelSolver`
- `modi/ModiSolver` (+ `modi/CicloSteppingStone`) — corre los 3 iniciales, arranca del más
  barato, optimiza con u/v + ciclo; maneja degeneración con celdas ε (union-find)

### application/lp/
- `SimplexUseCase` / `SimplexService`
- `GranMUseCase` / `GranMService`
- `DosFasesUseCase` / `DosFasesService`
- `GraficoUseCase` / `GraficoService`

### application/transporte/
- `TransporteUseCase` / `TransporteService` — fachada única: despacha al solver según `modelo.metodo()`

### infrastructure/lp/
- `SimplexController` — `POST /api/v1/lp/simplex`
- `GranMController` — `POST /api/v1/lp/gran-m`
- `DosFasesController` — `POST /api/v1/lp/dos-fases`
- `GraficoController` — `POST /api/v1/lp/grafico`

### infrastructure/transporte/
- `TransporteController` — `POST /api/v1/transporte/{esquina-noroeste,costo-minimo,vogel,modi}`
  (cada endpoint fuerza su método; bind directo de `ModeloTransporte`)

### infrastructure/ai/
- `TutorAiService` — interfaz conversacional, memoria en RAM por sesión (30 mensajes)
- `ModeloAiService` — interfaz con structured output: `extraerModelo()` y `validarModelo()`
- `AiConfig` — beans manuales via `AiServices.builder()`; registra las 7 tools;
  envuelve el `ChatModel` en `RetryingChatModel` antes de pasarlo a los services
- `RetryingChatModel` — decorador del `ChatModel`: reintenta hasta 3 veces cuando Groq
  devuelve 400 `tool_use_failed` (el LLM generó la tool call con sintaxis malformada);
  los reintentos van con temperatura 0.4 para romper el determinismo de temperature=0.
  Si se agotan, `AiChatController` degrada con un mensaje amable (nunca el error crudo)
- `ChatContextStore` — ThreadLocal que las tools usan para escribir datos estructurados;
  guarda también el `sesionId` del turno; el controlador lo lee al terminar el chat
  y lo incluye en `ChatResponse`
- `tools/SimplexTool` — `@Tool resolverSimplex(...)` — solo LEQ — **solicita aprobación HITL**
- `tools/GranMTool` — `@Tool resolverGranM(...)` — LEQ/GEQ/EQ — **solicita aprobación HITL**
- `tools/DosFasesTool` — `@Tool resolverDosFases(...)` — LEQ/GEQ/EQ (por defecto) — **solicita aprobación HITL**
- `tools/GraficoTool` — `@Tool resolverGrafico(...)` — 2 variables — **solicita aprobación HITL**
- `tools/TransporteTool` — `@Tool resolverTransporte(...)` — oferta/demanda/costos + método — **solicita aprobación HITL**
  (la matriz de costos va como `List<FilaCostos>`, NO `List<List<Double>>`: LangChain4j 1.13.0 no
  genera el esquema JSON de genéricos anidados — ver §9)
- `tools/SugerirModeloTool` — `@Tool registrarModeloSugerido(...)`
- `tools/ValidarModeloTool` — `@Tool registrarValidacion(...)`
- `tools/SolicitudAprobacionHelper` — paso común: crea la solicitud HITL y avisa al LLM
  (tipado a `ModeloResoluble`, sirve a LP y Transporte)
- `AiChatController` — endpoints AI; `/chat` inicializa el store, llama al tutor
  y devuelve `ChatResponse`; `/chat/aprobacion` recibe la decisión humana, reanuda el
  workflow y reanuda al tutor con el desenlace en un mensaje `[SISTEMA]`
- `dto/` — ChatRequest, ChatResponse (respuesta + 6 campos nullables: modeloSugerido, validacion,
           resultado, resultadoGrafico, resultadoTransporte, solicitudAprobacion), SolicitudAprobacion
           (modelo tipado `ModeloResoluble`), DecisionAprobacionRequest, SugerirModeloRequest,
           ModeloSugeridoResponse, ValidarModeloRequest, ValidacionResponse

### infrastructure/ai/hitl/ — Human-in-the-Loop (IMPLEMENTADO, langchain4j-agentic)
Ningún solver se ejecuta desde el chat sin aprobación humana explícita — garantía
estructural, no de prompt. Ver §7 para el flujo completo.
- `ResolucionAprobadaWorkflow` — workflow agéntico secuencial: compuerta `HumanInTheLoop`
  (publica un `PendingResponse` en el `AgenticScope`) → acción resolutora (se bloquea
  leyendo la decisión; solo resuelve si `aprobado=true`)
- `HitlConfig` — construye el workflow con `AgenticServices.sequenceBuilder(...)`;
  ejecutor de hilos virtuales para las invocaciones bloqueadas; `@EnableScheduling`
- `AprobacionHumanaService` — `solicitar()` (lanza el workflow en background),
  `decidir()` (completa el PendingResponse "en caliente", espera el desenlace y evacúa
  el scope), `limpiarExpiradas()` (@Scheduled: descarta solicitudes sin decisión >15 min)
- `SolicitudAprobacionRegistry` — registro en RAM de solicitudes en vuelo
- `ResolucionEjecutor` — único punto que invoca los use cases de resolución (LP + Transporte);
  su record `Ejecucion` tiene 3 resultados (solo uno non-null: `resultado` tabular LP,
  `resultadoGrafico`, o `resultadoTransporte`); genera el resumen textual que el tutor usa
- `MetodoResolucion` (enum): SIMPLEX, GRAN_M, DOS_FASES, GRAFICO, **TRANSPORTE** (el submétodo
  de transporte viaja dentro del `ModeloTransporte`, no como valores de enum aparte)
- `DecisionAprobacion` (record)
- Toda la cadena (`SolicitudAprobacion`, `Registry`, `AprobacionHumanaService`, `Workflow`)
  está tipada a `ModeloResoluble`, no a `ModeloLP` — así admite cualquier módulo futuro

### infrastructure/web/
- `GlobalExceptionHandler` — IllegalArgumentException → HTTP 400
- `WebConfig` — CORS para `localhost:*`

### resources/
- `prompts/tutor_system_prompt.txt` — system prompt socrático adaptativo (carga en runtime)
- `db/migration/V1__init.sql` — tablas: sesion, problema_resuelto, interaccion_ia

### test/
- `SimplexSolverTest` — 7 tests de dominio sin Spring (incluye holguras, precios sombra, rangos)
- `GranMSolverTest` — 5 tests: LEQ+GEQ, todo-GEQ, EQ, infactible, pasos
- `DosFasesSolverTest` — 5 tests: LEQ+GEQ, todo-GEQ, EQ, infactible, fases en orden
- `domain/transporte/*` — `EsquinaNoroesteSolverTest`, `CostoMinimoSolverTest`, `VogelSolverTest`,
  `ModiSolverTest` (óptimo conocido 240/30, itera θ>0 en el ejemplo Taha 102→100, degeneración,
  comparativa, balanceo, validación), `CicloSteppingStoneTest`
- `infrastructure/transporte/TransporteControllerTest` — controller + serialización Jackson
- `infrastructure/ai/tools/TransporteToolSchemaTest` — el esquema JSON del `@Tool` se genera sin crash
- `ResolucionAprobadaWorkflowTest` — HITL end-to-end (incluye caso TRANSPORTE devolviendo resultado)

---

## 6. Endpoints implementados

| Método | URL | Función |
|---|---|---|
| POST | `/api/v1/lp/simplex` | Simplex estándar (solo ≤); devuelve pasos + análisis post-óptimo |
| POST | `/api/v1/lp/gran-m` | Gran M (≤/≥/=); devuelve pasos + análisis post-óptimo |
| POST | `/api/v1/lp/dos-fases` | Dos Fases (≤/≥/=); devuelve pasos + análisis post-óptimo |
| POST | `/api/v1/lp/grafico` | Método gráfico (2 variables); región factible + vértices |
| POST | `/api/v1/transporte/esquina-noroeste` | Solución básica inicial (esquina noroeste) |
| POST | `/api/v1/transporte/costo-minimo` | Solución básica inicial (costo mínimo) |
| POST | `/api/v1/transporte/vogel` | Solución básica inicial (Vogel/VAM) |
| POST | `/api/v1/transporte/modi` | Óptimo por MODI (compara las 3 iniciales y optimiza) |
| POST | `/api/v1/ai/chat` | Chat socrático con memoria de sesión |
| POST | `/api/v1/ai/chat/aprobacion` | HITL: decisión humana (aprobar/rechazar) sobre la solicitud de resolución pendiente |
| POST | `/api/v1/ai/sugerir-modelo` | Extrae `ModeloLP` desde lenguaje natural |
| POST | `/api/v1/ai/validar-modelo` | Valida modelo del estudiante contra enunciado |

Los tres solvers LP tabulares incluyen en `solution`: `valores`, `holguras`, `preciosSombra` y
`rangosSensibilidad`. Los de transporte devuelven `SolucionTransporte` (`asignaciones`, `costoTotal`,
`comparativaInicial`, `metodoInicial`). Todos comparten el envoltorio `SolveResult<T>` (status + steps).

Ver `docs/API_CONTRACT.md` para los cuerpos de request/response completos.

---

## 7. Capa de IA — cómo funciona

Tres responsabilidades separadas (detalle en `docs/ARQUITECTURA_IA.md`):

- **Comportarse** → `prompts/tutor_system_prompt.txt` — tutor socrático adaptativo
- **Hacer** → `@Tool` en `infrastructure/ai/tools/` — nunca en domain
- **Saber** → RAG con ChromaDB — **IMPLEMENTADO** (ver detalles abajo)

### RAG — Retrieval Augmented Generation (IMPLEMENTADO )

El tutor accede a un corpus de teoría de IO mediante embeddings. El corpus tiene dos fuentes:

**Corpus — archivos `.md` de teoría** (`src/main/resources/corpus/lp/`):
- `01_que_es_programacion_lineal.md` — definición, cuándo aplica, características formales
- `02_como_formular_un_modelo_lp.md` — paso a paso de formulación, ejemplos
- `03_metodo_simplex_teoria.md` — tableau, regla Dantzig, razón mínima, pivote, optimalidad
- `04_interpretacion_de_resultados.md` — lectura de solución, holguras (sᵢ=0 vs sᵢ>0), precios sombra
- `05_casos_especiales.md` — infactible, no acotado, óptimos múltiples, degeneración
- `06_errores_comunes_al_modelar.md` — 6 errores frecuentes de formulación (MAX/MIN, coeficientes, etc.)

**Corpus — libro de texto** (`src/main/resources/corpus/`):
- `investigacion-de-operaciones-taha-hamdy-2004.pdf` — libro completo de IO (200 págs, todos los módulos)
- Va en la raíz de `corpus/` (no en `lp/`) porque cubre transporte, redes, PD, inventarios, etc.
- Parseado con `ApachePdfBoxDocumentParser` (`langchain4j-document-parser-apache-pdfbox:1.13.0-beta23`)

**Estructura del corpus**:
```
src/main/resources/corpus/
├── investigacion-de-operaciones-taha-hamdy-2004.pdf   ← libro general (todos los módulos)
└── lp/
    ├── 01_que_es_programacion_lineal.md
    ├── 02_como_formular_un_modelo_lp.md
    ├── 03_metodo_simplex_teoria.md
    ├── 04_interpretacion_de_resultados.md
    ├── 05_casos_especiales.md
    └── 06_errores_comunes_al_modelar.md
```

**Ingesta** — `infrastructure/ai/rag/CorpusIngester.java`:
- **Markdown** (`*.md`): chunking 350 chars + 30 overlap → ~60 chunks de notas de teoría LP
- **PDF** (`*.pdf`): chunking 700 chars + 70 overlap → ~300–600 chunks del libro (prosa densa)
- Ambas fuentes van a la misma colección `io-corpus` en ChromaDB
- Toggle `app.rag.incluir-pdf` (`RAG_INCLUIR_PDF`) para omitir el PDF en re-ingestas rápidas

**Retrieval** — `infrastructure/ai/rag/RagConfig.java`:
- Bean `EmbeddingModel`: AllMiniLmL6V2QuantizedEmbeddingModel (local, ~100MB)
- Bean `ContentRetriever`: máximo 6 fragmentos por consulta, score mínimo 0.5
- Inyectado automáticamente en `TutorAiService` via `.contentRetriever(...)`

**Cómo funciona** — en cada turno del chat:
1. Estudiante pregunta → LangChain4j embede la pregunta
2. Busca en ChromaDB los 6 fragmentos más similares (score ≥ 0.5) — de MD o PDF indistintamente
3. Los inyecta como contexto en el prompt del LLM
4. El tutor responde fundamentado en la teoría, no solo en conocimiento general

**Notas críticas**:
- ChromaDB debe estar corriendo: `docker compose up chromadb -d`
- ChromaDB v2 API (no v1) — LangChain4j 1.13.0-beta23 requiere `.apiVersion(ChromaApiVersion.V2)`
- Excluir `langchain4j-http-client-jdk` en `build.gradle` para evitar conflicto con Spring RestClient
- El `ModeloAiService` (extracción/validación estructurada) NO recibe ContentRetriever — no lo necesita
- Si el PDF es un escaneo sin capa de texto, PDFBox no extrae nada — verificar log `[RAG] PDF extraído: X caracteres`

**Cómo re-ingestar según el escenario**:
```bash
# Setup inicial o si cambia el libro:
RAG_REINGESTAR=true RAG_INCLUIR_PDF=true gradle bootRun    # MD + PDF (~2-4 min)

# Solo editaste archivos .md de teoría:
RAG_REINGESTAR=true RAG_INCLUIR_PDF=false gradle bootRun   # solo MD (~10 s)

# Arranque normal (no re-ingesta):
# RAG_REINGESTAR=false es el default en application.yaml
gradle bootRun
```

---

### Human-in-the-Loop (HITL) — aprobación humana antes de resolver

El tutor NO da la solución directamente. Las tools de resolución solo se invocan cuando:
1. El modelo está completamente validado
2. El estudiante lo pide explícitamente

Y además — desde la introducción del HITL — **invocar la tool NO resuelve**: la garantía
"sin aprobación humana no corre ningún solver" es estructural (código), no de prompt.

Flujo completo (dos turnos):
1. Estudiante pide resolver → el tutor invoca `resolverX` → la tool crea una
   `SolicitudAprobacion` y lanza en background el workflow agéntico, que queda
   **bloqueado** en la compuerta `HumanInTheLoop` (un `PendingResponse` sin hilo de fondo).
   `ChatResponse.solicitudAprobacion` lleva el modelo + método a la UI (botones Aprobar/Rechazar).
2. La UI envía la decisión a `POST /api/v1/ai/chat/aprobacion`:
   - **Aprobar** → se completa el `PendingResponse` → el workflow despierta y ejecuta el
     solver → el tutor recibe el resumen en un mensaje `[SISTEMA]` y explica el resultado.
     El `ChatResponse` trae `resultado`/`resultadoGrafico`/`resultadoTransporte` + la explicación.
   - **Rechazar** → el solver no corre; el comentario del estudiante re-alimenta al tutor
     (típicamente responde con un `modeloSugerido` corregido).

Ciclo de vida: una nueva solicitud de la misma sesión reemplaza la anterior; las
solicitudes sin decisión expiran a los 15 minutos (`limpiarExpiradas`, @Scheduled) y
su `AgenticScope` se evacúa (`evictAgenticScope`).

La memoria de sesión es **en RAM** (se pierde al reiniciar). La persistencia en PostgreSQL
(tabla `sesion`) está pendiente de implementar con `ChatMemoryStore`. Las solicitudes HITL
también viven en RAM — persistirlas requeriría un `AgenticScopeStore`.

---

## 8. Convenciones de código

- **Idioma:** dominio de IO en **español** (`Restriccion`, `FuncionObjetivo`, `Grafo`).
  Términos técnicos de framework en inglés. Comentarios en español.
- **Inmutabilidad:** usa `record` para datos de entrada/salida. Los solvers no mutan su input.
- **Un solver = una clase** con un método público `resolver(...)`. La lógica auxiliar, privada.
- **Nombres de endpoints:** `/api/v1/{módulo}/{recurso}` (ver `docs/API_CONTRACT.md`).

---

## 9. Qué NO hacer

- ❌ No metas `@Service`/`@Component`/`@Tool`/`@Entity` en `domain`.
- ❌ No implementes los módulos TODO sin instrucción explícita.
- ❌ No hardcodees el identificador del modelo ni la API key.
- ❌ No hagas que la IA devuelva la respuesta final sin paso socrático.
- ❌ No conviertas un resultado "infactible/no acotado" en una excepción.
- ❌ No uses `ChatLanguageModel` — en LangChain4j 1.13.0 es `ChatModel`.
- ❌ No uses `@AiService` del starter para nuevos services — usa `AiServices.builder()`.
- ❌ No modifiques los archivos `.md` del corpus sin re-ingestar (`RAG_REINGESTAR=true`).
- ❌ No pongas el PDF del libro en `corpus/lp/` — va en la raíz de `corpus/` porque cubre todos los módulos.
- ❌ No pases parámetros `@Tool` con **genéricos anidados** (`List<List<Double>>`, `Map<..,List<..>>`):
  LangChain4j 1.13.0 falla al generar su esquema JSON (`ParameterizedTypeImpl cannot be cast to Class`)
  y el bean `tutorAiService` no arranca. Envuelve la lista interna en un `record` (ver `TransporteTool.FilaCostos`).
- ❌ No re-tipes la cadena HITL a un módulo concreto — usa `ModeloResoluble` (interfaz marcador en `domain/common`).

---

## 10. Cómo agregar un nuevo solver al chat (guía)

Cuando el docente indique implementar Dos Fases, Gran M, Transporte, etc., el patrón
es siempre el mismo. Toma como referencia `SimplexTool` + `SimplexUseCase`.

### Paso 1 — Dominio (Java puro, sin Spring)
```
domain/{módulo}/
  Modelo{Módulo}.java          ← record con los datos de entrada
  Solucion{Módulo}.java        ← record con los datos de salida
  {Algoritmo}Solver.java       ← lógica pura, método resolver(Modelo) → SolveResult<Solucion>
```
- El solver devuelve `SolveResult<Solucion{Módulo}>` — nunca lanza excepción por infactible.
- Para LP con restricciones GEQ/EQ: mismos records `ModeloLP`/`SolucionLP`, solver nuevo.

### Paso 2 — Aplicación (puerto)
```
application/{módulo}/
  {Algoritmo}UseCase.java      ← interfaz: resolver(Modelo) → SolveResult<Solucion>
  {Algoritmo}Service.java      ← @Service que delega al solver del dominio
```

### Paso 3 — Infraestructura REST
```
infrastructure/{módulo}/
  {Algoritmo}Controller.java   ← POST /api/v1/{módulo}/{algoritmo}
```

### Paso 4 — Tool para el chat
```java
// infrastructure/ai/tools/{Algoritmo}Tool.java
@Slf4j
@Component
public class {Algoritmo}Tool {

    private final {Algoritmo}UseCase useCase;
    private final ChatContextStore contextStore;

    @Tool("Descripción clara de cuándo el tutor debe invocar esta tool...")
    public String resolver{Algoritmo}(...parámetros...) {
        // 1. construir el modelo desde los parámetros
        // 2. llamar al useCase
        // 3. contextStore.obtener().resultado = resultado   ← igual que SimplexTool
        // 4. return formatearParaTutor(resultado)
    }
}
```
> Si el resultado es un tipo distinto a `SolucionLP`, agregar el campo correspondiente
> en `ChatContextStore.DatosRespuesta` y en `ChatResponse`.

### Paso 5 — Registrar la tool en AiConfig
```java
@Bean
public TutorAiService tutorAiService(ChatModel chatModel,
                                     SimplexTool simplexTool,
                                     SugerirModeloTool sugerirTool,
                                     ValidarModeloTool validarTool,
                                     {Algoritmo}Tool nuevoTool) throws IOException {
    return AiServices.builder(TutorAiService.class)
            ...
            .tools(simplexTool, sugerirTool, validarTool, nuevoTool)   // ← agregar aquí
            .build();
}
```

### Paso 6 — Actualizar el system prompt
En `resources/prompts/tutor_system_prompt.txt`:
- Mover el algoritmo de "EN DESARROLLO" a "DISPONIBLE"
- Describir cuándo el tutor debe llamar a la nueva tool (qué tipo de restricciones,
  qué detecta en el modelo que le indica usar este método en lugar de otro)

### Eso es todo
El `ChatContextStore`, `ChatResponse`, `AiChatController` y el frontend no necesitan
cambios si el resultado es `SolveResult<SolucionLP>`. El tutor detecta automáticamente
el método correcto por el tipo de restricciones del modelo.

---

## 11. Documentación de referencia

- `docs/ARQUITECTURA_IA.md` — detalle de tools, ChatContextStore, system prompt y flujo
- `docs/API_CONTRACT.md` — endpoints implementados y pendientes, request/response JSON
- `docs/ESTRUCTURA_PAQUETES.md` — árbol de paquetes real del proyecto
- `docs/MODULOS_IO.md` — especificación matemática de cada solver
- `docs/FRONTEND_INTEGRATION.md` — guía completa para el agente React
- `docs/RAG_IMPLEMENTATION_GUIDE.md` — (COMPLETADO ) guía de la implementación RAG
- `docs/RETRY_LLM.md` — retry ante `tool_use_failed` de Groq (RetryingChatModel + fallback del controlador)
- `docs/TRANSPORTE.md` — (COMPLETADO) módulo Transporte de punta a punta: dominio, MODI, HITL generalizado, grafo de red
- `docs/GUIA_REDES.md` — **guía para la próxima sesión**: cómo implementar el módulo Redes reusando todo lo de Transporte
