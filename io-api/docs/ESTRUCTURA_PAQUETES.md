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
│       ├── SolucionLP.java                        record(valores, holguras, valorOptimo, preciosSombra, rangosSensibilidad)
│       ├── RangosSensibilidad.java                record(coeficientesObjetivo, rhs)
│       ├── RangoCoeficiente.java                  record(variable, valorActual, min, max) — null = ±∞
│       ├── RangoRHS.java                          record(restriccion, valorActual, min, max) — null = ±∞
│       ├── SensibilidadCalculator.java            utilidad estática: holguras, precios sombra, rangos
│       ├── TipoObjetivo.java                      enum: MAXIMIZAR, MINIMIZAR
│       ├── TipoRestriccion.java                   enum: LEQ, GEQ, EQ
│       ├── simplex/
│       │   └── SimplexSolver.java                 Simplex estándar (solo LEQ, b>=0); usa SensibilidadCalculator
│       ├── granm/
│       │   └── GranMSolver.java                   Gran M — LEQ/GEQ/EQ; usa SensibilidadCalculator
│       ├── dosfases/
│       │   └── DosFasesSolver.java                Dos Fases — LEQ/GEQ/EQ; usa SensibilidadCalculator
│       └── grafico/
│           ├── GraficoSolver.java                 Método gráfico (2 variables)
│           ├── SolucionGrafica.java               record(valores, valorOptimo, vertices, region, xMax, yMax)
│           └── PuntoVertice.java                  record(x, y, valorZ, esOptimo, etiqueta)
│   common/
│       └── ModeloResoluble.java                   interfaz marcador (la implementan ModeloLP y ModeloTransporte)
│   transporte/                                     ✅ IMPLEMENTADO
│       ├── ModeloTransporte.java                  record(origenes, destinos, oferta, demanda, costos, metodo) implements ModeloResoluble
│       ├── SolucionTransporte.java                record(origenes, destinos, asignaciones, costoTotal, comparativaInicial, metodoInicial)
│       ├── MetodoTransporte.java                  enum: ESQUINA_NOROESTE, COSTO_MINIMO, VOGEL, MODI
│       ├── CostoPorMetodo.java                    record(metodo, costoInicial)
│       ├── Balanceador.java                       agrega origen/destino ficticio si Σoferta≠Σdemanda; valida
│       ├── TransporteUtils.java                   costo total, snapshots, mapa `datos` de cada paso
│       ├── esquinanoroeste/EsquinaNoroesteSolver.java
│       ├── costominimo/CostoMinimoSolver.java
│       ├── vogel/VogelSolver.java
│       └── modi/{ModiSolver.java, CicloSteppingStone.java}   corre los 3 iniciales, u/v + ciclo, degeneración
│
│   ├── redes/                                  ✅ IMPLEMENTADO — ver docs/GUIA_REDES.md
│   │   ├── {ModeloRed, SolucionRed, MetodoRed, Arista}.java
│   │   ├── {RedValidador, RedUtils}.java
│   │   └── dijkstra/ kruskal/ edmondskarp/ flujocostominimo/ asignacion/
│   │
│   ├── entera/                                 ✅ IMPLEMENTADO
│   │   ├── {ModeloEntero, SolucionEntera, TipoVariable}.java
│   │   └── branchandbound/BranchAndBoundSolver.java   relajación resuelta por GranMSolver
│   │
│   ├── inventario/                             ✅ IMPLEMENTADO — ver docs/INVENTARIOS.md
│   │   ├── {ModeloInventario, SolucionInventario, MetodoInventario}.java
│   │   ├── {TramoDescuento, ComparativaTramo, InventarioUtils, InventarioValidador}.java
│   │   └── eoqbasico/ descuentos/ faltantes/ produccion/ reorden/
│   │
│   └── dinamica/                               ✅ IMPLEMENTADO — ver docs/DINAMICA.md
│       ├── ModeloDinamico.java                 record(metodo, sentido, + campos por submodelo) + Builder
│       ├── SolucionDinamica.java               record(valorOptimo, tablas, politicaOptima, rutaOptima,
│       │                                              definicionEtapas/Estados/Decisiones,
│       │                                              funcionRecurrencia, principioOptimalidad,
│       │                                              interpretacionPolitica) + Builder
│       ├── {MetodoDinamico, SentidoOptimizacion}.java        enums
│       ├── {TablaEtapa, FilaEtapa, EvaluacionDecision, DecisionOptima}.java   tabla de solución
│       ├── {ActividadRecurso, ArticuloMochila, EtapaRuta, ArcoRuta, DatosEdadEquipo}.java   entradas
│       ├── {DinamicaUtils, DinamicaValidador}.java
│       └── asignacion/ mochila/ ruta/ produccion/ reemplazo/   un solver por submodelo
│
├── application/                                   ◄ Casos de uso y puertos
│   └── lp/
│       ├── SimplexUseCase.java                    interfaz: resolver(ModeloLP) → SolveResult<SolucionLP>
│       ├── SimplexService.java                    @Service que delega a SimplexSolver
│       ├── GranMUseCase.java                      interfaz: resolver(ModeloLP) → SolveResult<SolucionLP>
│       ├── GranMService.java                      @Service que delega a GranMSolver
│       ├── DosFasesUseCase.java                   interfaz: resolver(ModeloLP) → SolveResult<SolucionLP>
│       ├── DosFasesService.java                   @Service que delega a DosFasesSolver
│       ├── GraficoUseCase.java / GraficoService.java
│   └── transporte/
│       ├── TransporteUseCase.java                 interfaz: resolver(ModeloTransporte) → SolveResult<SolucionTransporte>
│       └── TransporteService.java                 @Service; despacha al solver según modelo.metodo()
│
└── infrastructure/                                ◄ Spring, JPA, LangChain4j, REST
    ├── lp/
    │   ├── SimplexController.java                 POST /api/v1/lp/simplex
    │   ├── GranMController.java                   POST /api/v1/lp/gran-m
    │   ├── DosFasesController.java                POST /api/v1/lp/dos-fases
    │   └── GraficoController.java                 POST /api/v1/lp/grafico
    ├── transporte/
    │   └── TransporteController.java              POST /api/v1/transporte/{esquina-noroeste,costo-minimo,vogel,modi}
    │
    ├── ai/
    │   ├── AiConfig.java                          @Configuration — beans manuales AiServices.builder()
    │   ├── AiChatController.java                  POST /api/v1/ai/{chat, sugerir-modelo, validar-modelo, chat/aprobacion}
    │   │                                          GET  /api/v1/ai/chat/{sesionId}/{historial, actividad}
    │   ├── actividad/
    │   │   ├── FaseActividad.java                 Las 7 fases + la plantilla del texto que ve el estudiante
    │   │   ├── Actividad.java                     record: fase, texto, secuencia (monótona)
    │   │   ├── ActividadRegistry.java             Mapa por sesión (RAM) — NO ThreadLocal: lo lee otra petición
    │   │   └── EtiquetaMetodo.java                Nombre real del algoritmo (MODI, Vogel…), no el del enum
    │   ├── supervisor/
    │   │   ├── TutorSupervisorService.java        Orquestador principal y enrutador por sesión
    │   │   └── ModuloClassifierService.java       Clasificador semántico LLM de módulo IO
    │   ├── subagents/
    │   │   ├── PlSubAgent.java                    Subagente de Programación Lineal Continua
    │   │   ├── InventarioSubAgent.java            Subagente de Inventarios
    │   │   ├── TransporteSubAgent.java            Subagente de Transporte
    │   │   ├── RedesSubAgent.java                 Subagente de Redes
    │   │   ├── EnteraSubAgent.java                Subagente de PL Entera
    │   │   └── DinamicaSubAgent.java              Subagente de Programación Dinámica
    │   ├── hitl/
    │   │   ├── AprobacionHumanaService.java       Servicio de compuerta Human-in-the-loop y sincronización de modelo
    │   │   ├── ResolucionAprobadaWorkflow.java    Agentic workflow para ejecución post-aprobación
    │   │   └── SolicitudAprobacionRegistry.java   Registro en memoria de solicitudes pendientes
    │   ├── tools/                                 Herramientas especializadas por subagente (@Tool)
    │   └── dto/                                   DTOs de peticiones/respuestas del chat y compuerta HITL
    │   ├── rag/                                   Módulo de recuperación de información (RAG)
    │   └── persistence/                           Entidades y repositorios JPA
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
└── domain/lp/
    ├── simplex/
    │   └── SimplexSolverTest.java                 7 tests: MAX, MIN, no-acotado, validación,
    │                                              pasos, holguras/precios-sombra/rangos, restricción no activa
    ├── granm/
    │   └── GranMSolverTest.java                   5 tests: LEQ+GEQ, todo-GEQ, EQ, infactible, pasos
    └── dosfases/
        └── DosFasesSolverTest.java                5 tests: LEQ+GEQ, todo-GEQ, EQ, infactible, fases en orden
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
- Persistida en PostgreSQL: `infrastructure/ai/memory/PostgresChatMemoryStore` sobre `chat_memory`
- `MessageWindowChatMemory` de 14 mensajes, con `memoryId = sesionId` **compartido** por los
  seis subagentes (una sola ventana por sesión)
- Sobrevive al reinicio del servidor; se purga por inactividad (`app.sesion.retencion-dias`)

### Persistencia (`infrastructure/persistence`)
- `entity/` + `repository/` — JPA nunca en `domain`
- `ddl-auto: validate`: las entidades deben calzar exactamente con las migraciones Flyway
