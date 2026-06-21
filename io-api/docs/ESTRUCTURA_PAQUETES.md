# Estructura de paquetes — Estado real del proyecto

> Este árbol refleja lo que **existe actualmente** en el código.
> Lo que está marcado como `⏳ pendiente` es la dirección de diseño, no existe aún.

Paquete raíz: `jpap.dev.io_api`

```
io-api/src/main/java/jpap/dev/io_api/
│
├── IoApiApplication.java                          ← @SpringBootApplication
│
├── domain/                                        ◄ JAVA PURO. Sin Spring, sin IA, sin JPA.
│   ├── common/
│   │   ├── SolveResult.java                       record<T>(status, solution, steps)
│   │   ├── SolveStep.java                         record(numero, titulo, descripcion, datos)
│   │   └── SolveStatus.java                       enum: OPTIMO, INFACTIBLE, NO_ACOTADO, MULTIPLE_OPTIMO, ERROR
│   │
│   └── lp/                                        ✅ Programación Lineal — IMPLEMENTADO
│       ├── ModeloLP.java                          record(variables, objetivo, restricciones)
│       ├── FuncionObjetivo.java                   record(coeficientes, tipo)
│       ├── Restriccion.java                       record(coeficientes, tipo, rhs)
│       ├── SolucionLP.java                        record(valores: Map<String,Double>, valorOptimo)
│       ├── TipoObjetivo.java                      enum: MAXIMIZAR, MINIMIZAR
│       ├── TipoRestriccion.java                   enum: LEQ, GEQ, EQ
│       └── simplex/
│           └── SimplexSolver.java                 algoritmo completo con pasos (solo LEQ, b>=0)
│
│   ⏳ transporte/ (TODO estructurado)
│   ⏳ redes/      (TODO estructurado)
│   ⏳ entera/     (TODO estructurado)
│   ⏳ dinamica/   (TODO estructurado)
│   ⏳ inventarios/(TODO estructurado)
│
├── application/                                   ◄ Casos de uso y puertos
│   └── lp/
│       ├── SimplexUseCase.java                    interfaz: resolver(ModeloLP) → SolveResult<SolucionLP>
│       └── SimplexService.java                    @Service que delega a SimplexSolver
│
└── infrastructure/                                ◄ Spring, JPA, LangChain4j, REST
    ├── lp/
    │   └── SimplexController.java                 POST /api/v1/lp/simplex
    │
    ├── ai/
    │   ├── TutorAiService.java                    interfaz conversacional (@MemoryId, @UserMessage)
    │   ├── ModeloAiService.java                   interfaz structured output (extracción + validación)
    │   ├── AiConfig.java                          @Configuration — beans manuales AiServices.builder()
    │   ├── AiChatController.java                  POST /api/v1/ai/{chat, sugerir-modelo, validar-modelo}
    │   ├── tools/
    │   │   └── SimplexTool.java                   @Component con @Tool resolverSimplex(...)
    │   └── dto/
    │       ├── ChatRequest.java                   record(sesionId, mensaje)
    │       ├── ChatResponse.java                  record(sesionId, respuesta)
    │       ├── SugerirModeloRequest.java           record(descripcionProblema)
    │       ├── ModeloSugeridoResponse.java         record(modelo, razonamiento, supuestos, advertencias)
    │       ├── ValidarModeloRequest.java           record(descripcionProblema, modelo)
    │       └── ValidacionResponse.java             record(esValido, analisis, errores, sugerencias, modeloCorregido)
    │
    │   ⏳ ai/rag/         (ChromaDB — pendiente)
    │   ⏳ persistence/    (JPA entities + repositories — pendiente)
    │
    └── web/
        ├── GlobalExceptionHandler.java            @RestControllerAdvice (400/500)
        └── WebConfig.java                         CORS para localhost:*

io-api/src/main/resources/
├── application.yaml                               config: DB, LangChain4j (Groq), Chroma, logging
├── prompts/
│   └── tutor_system_prompt.txt                    system prompt socrático (carga en runtime)
└── db/migration/
    └── V1__init.sql                               tablas: sesion, problema_resuelto, interaccion_ia

io-api/src/test/java/jpap/dev/io_api/
└── domain/lp/simplex/
    └── SimplexSolverTest.java                     5 tests (sin Spring): MAX, MIN, no-acotado,
                                                   validación inputs, estructura de pasos
```

---

## Reglas de capa

### Lo que NUNCA debe ir en `domain/`
- Anotaciones: `@Service`, `@Component`, `@Repository`, `@Entity`
- Imports de: `org.springframework.*`, `dev.langchain4j.*`, `jakarta.persistence.*`
- Si una clase de dominio necesita Spring → está en la capa equivocada

### Lo que NUNCA debe ir en `domain/` ni `application/`
- `@Tool` (LangChain4j) → solo en `infrastructure/ai/tools/`
- `@RestController` → solo en `infrastructure/`
- Anotaciones JPA → solo en `infrastructure/persistence/`

### Dirección de las dependencias
```
infrastructure → application → domain
```
El dominio no conoce a nadie. La aplicación no conoce a la infraestructura.

---

## Notas de implementación importantes

### LangChain4j 1.13.0
- `ChatModel` (no `ChatLanguageModel`) — `dev.langchain4j.model.chat.ChatModel`
- Builder: `.chatModel(bean)` (no `.chatLanguageModel(...)`)
- No usar `@AiService` del starter → usar `AiServices.builder()` manual en `AiConfig`
- `@Tool` → `dev.langchain4j.agent.tool.Tool`
- `@P` → `dev.langchain4j.agent.tool.P` (descripción de parámetros de tool)
- `@Description` → `dev.langchain4j.model.output.structured.Description` (campos de structured output)

### Records y Jackson
- El flag `-parameters` está activado en `build.gradle` → Jackson deserializa records sin `@JsonCreator`
- Los records del dominio se usan directamente como request/response bodies (sin DTOs extra)

### Memoria de sesión
- En RAM, `MessageWindowChatMemory.withMaxMessages(30)`
- Se pierde al reiniciar el servidor
- Pendiente: `ChatMemoryStore` con tabla `sesion` en PostgreSQL
