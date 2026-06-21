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
| Programación Lineal (LP) | **Simplex estándar IMPLEMENTADO** | Simplex estándar (≤, b≥0); Dos Fases, Gran M, Dual, Sensibilidad, Gráfico — pendientes |
| Transporte | TODO estructurado | Esquina Noroeste, Costo Mínimo, Vogel (VAM); MODI; Húngaro (asignación) |
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
- **IA:** LangChain4j **1.13.0** (ver nota crítica abajo)
- **LLM:** OpenAI-compatible apuntado a **Groq** (`llama-3.3-70b-versatile`)
- **RAG vector store:** ChromaDB (pendiente de integrar)
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
- `ModeloLP`, `FuncionObjetivo`, `Restriccion`, `SolucionLP` — records inmutables
- `TipoObjetivo` (MAXIMIZAR/MINIMIZAR), `TipoRestriccion` (LEQ/GEQ/EQ)
- `simplex/SimplexSolver` — Simplex estándar completo con registro de pasos por iteración

**Limitación del SimplexSolver:** solo acepta restricciones `LEQ` con `rhs >= 0`.
Para `GEQ` o `EQ` → Dos Fases o Gran M (pendientes).

### application/lp/
- `SimplexUseCase` (interfaz/puerto)
- `SimplexService` (@Service que delega a `SimplexSolver`)

### infrastructure/lp/
- `SimplexController` — `POST /api/v1/lp/simplex`

### infrastructure/ai/
- `TutorAiService` — interfaz conversacional, memoria en RAM por sesión (30 mensajes)
- `ModeloAiService` — interfaz con structured output: `extraerModelo()` y `validarModelo()`
- `AiConfig` — beans manuales via `AiServices.builder()`; registra las 3 tools
- `ChatContextStore` — ThreadLocal que las tools usan para escribir datos estructurados;
  el controlador los lee al terminar el chat y los incluye en `ChatResponse`
- `tools/SimplexTool` — `@Tool resolverSimplex(...)` — escribe `SolveResult` en el store
- `tools/SugerirModeloTool` — `@Tool registrarModeloSugerido(...)` — escribe `ModeloLP`
- `tools/ValidarModeloTool` — `@Tool registrarValidacion(...)` — escribe `ValidacionResponse`
- `AiChatController` — tres endpoints AI; `/chat` inicializa el store, llama al tutor
  y devuelve `ChatResponse` enriquecido con los datos que las tools escribieron
- `dto/` — ChatRequest, ChatResponse (enriquecido: respuesta + 3 campos nullables),
           SugerirModeloRequest, ModeloSugeridoResponse, ValidarModeloRequest, ValidacionResponse

### infrastructure/web/
- `GlobalExceptionHandler` — IllegalArgumentException → HTTP 400
- `WebConfig` — CORS para `localhost:*`

### resources/
- `prompts/tutor_system_prompt.txt` — system prompt socrático adaptativo (carga en runtime)
- `db/migration/V1__init.sql` — tablas: sesion, problema_resuelto, interaccion_ia

### test/
- `SimplexSolverTest` — 5 tests de dominio sin Spring: MAX, MIN, no-acotado, validación, pasos

---

## 6. Endpoints implementados

| Método | URL | Función |
|---|---|---|
| POST | `/api/v1/lp/simplex` | Resuelve Simplex estándar, devuelve pasos completos |
| POST | `/api/v1/ai/chat` | Chat socrático con memoria de sesión |
| POST | `/api/v1/ai/sugerir-modelo` | Extrae `ModeloLP` desde lenguaje natural |
| POST | `/api/v1/ai/validar-modelo` | Valida modelo del estudiante contra enunciado |

Ver `docs/API_CONTRACT.md` para los cuerpos de request/response completos.

---

## 7. Capa de IA — cómo funciona

Tres responsabilidades separadas (detalle en `docs/ARQUITECTURA_IA.md`):

- **Comportarse** → `prompts/tutor_system_prompt.txt` — tutor socrático adaptativo
- **Hacer** → `@Tool` en `infrastructure/ai/tools/` — nunca en domain
- **Saber** → RAG con ChromaDB — **pendiente de implementar** (ver `docs/RAG_IMPLEMENTATION_GUIDE.md`)

El tutor NO da la solución directamente. Solo invoca `resolverSimplex` cuando:
1. El modelo está completamente validado
2. El estudiante lo pide explícitamente

La memoria de sesión es **en RAM** (se pierde al reiniciar). La persistencia en PostgreSQL
(tabla `sesion`) está pendiente de implementar con `ChatMemoryStore`.

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
- `docs/RAG_IMPLEMENTATION_GUIDE.md` — guía para implementar RAG con ChromaDB (corpus + ingesta + retriever)
