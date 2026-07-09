# Arquitectura de la Capa de Inteligencia Artificial — Asistente Pivot

La capa de IA de **Asistente Pivot** implementa una arquitectura **Multi-Agente Orquestada con Supervisor**, **Clasificador Semántico LLM** y validación pedagógica **Human-in-the-Loop (HITL)** con sincronización dinámica con el formulario de la UI.

---

## 1. Arquitectura Multi-Agente & Supervisor de Módulos

Para evitar el desbordamiento del contexto (*context overload*) y las tool calls malformadas que surgen al exponer decenas de herramientas simultáneas a un solo agente monolítico, el sistema divide las responsabilidades en 6 subagentes especializados y un orquestador central:

```mermaid
graph TD
    Client[Frontend / Estudiante] -->|POST /api/v1/ai/chat| Controller[AiChatController]
    Controller --> Supervisor[TutorSupervisorService]
    
    Supervisor -->|Clasificador LLM / Sesión| Classifier[ModuloClassifierService]
    Supervisor -->|Enrutamiento| Router{Módulo IO Detectado}
    
    Router -->|PL| PlAgent[PlSubAgent]
    Router -->|INVENTARIO| InvAgent[InventarioSubAgent]
    Router -->|TRANSPORTE| TransAgent[TransporteSubAgent]
    Router -->|REDES| RedAgent[RedesSubAgent]
    Router -->|ENTERA| EntAgent[EnteraSubAgent]
    Router -->|DINAMICA| DinAgent[DinamicaSubAgent]
```

### Subagentes Especializados (`jpap.dev.io_api.infrastructure.ai.subagents`)
Cada subagente se construye en `AiConfig` mediante `AiServices.builder(...)` con:
- Un **prompt de sistema especializado** para su dominio pedagógico (`src/main/resources/prompts/subagents/`).
- Una **memoria de chat por ventana** (`MessageWindowChatMemory.withMaxMessages(14)`).
- Únicamente las **herramientas específicas de su módulo**:
  - **PlSubAgent**: `SugerirModeloTool`, `ValidarModeloTool`, `SimplexTool`, `GranMTool`, `DosFasesTool`, `GraficoTool`.
  - **InventarioSubAgent**: `InventarioTool`.
  - **TransporteSubAgent**: `TransporteTool`.
  - **RedesSubAgent**: `RedTool`.
  - **EnteraSubAgent**: `EnteraTool`, `SugerirModeloTool`, `ValidarModeloTool`.
  - **DinamicaSubAgent**: `DinamicaTool`.

### Clasificador Semántico Inteligente (`ModuloClassifierService`)
En lugar de depender de palabras clave rígidas, `TutorSupervisorService` invoca `ModuloClassifierService` (un agente ligero con LLM) que analiza el mensaje entrante y clasifica con precisión si pertenece a un nuevo módulo (`PL`, `INVENTARIO`, `TRANSPORTE`, `REDES`, `ENTERA`, `DINAMICA`) o si es una continuación conversacional (`CONTINUAR`). El módulo activo se conserva por sesión en memoria (`sesionModulo`).

---

## 2. Flujo Human-in-the-Loop (HITL) y Sincronización con Formulario UI

Ningún solver matemático es ejecutado de forma autónoma por la IA sin la confirmación expresa del estudiante.

```mermaid
sequenceDiagram
    participant E as Estudiante / UI
    participant C as AiChatController
    participant T as Subagente / Tool
    participant H as AprobacionHumanaService
    participant W as ResolucionAprobadaWorkflow
    participant S as Solver IO

    E->>C: POST /api/v1/ai/chat (pide resolver)
    C->>T: subAgent.chat(...)
    T->>H: solicitar(sesionId, modelo, metodo)
    H->>W: arranca workflow en background
    T-->>C: devuelve solicitudId en ChatResponse
    C-->>E: tarjeta interactiva Aprobar / Rechazar

    Note over E: El estudiante edita el formulario UI (opcional)
    E->>C: POST /api/v1/ai/chat/aprobacion (aprobado=true, modeloModificado)
    C->>H: decidir(solicitudId, aprobado, comentario, modeloModificado)
    Note over H: Sustituye el modelo en vuelo por modeloModificado
    H->>W: completa compuerta HITL
    W->>S: ejecutar(modeloModificado, metodo)
    S-->>W: Ejecucion (resultado + resumen)
    W-->>H: retorna desenlace
    H-->>C: desenlace
    C-->>E: ChatResponse con resultado y explicación
```

### Sincronización 100% de Concordancia (`modeloModificado`)
1. Cuando la IA propone un modelo (`registrarModeloSugerido`), la UI lo dibuja en su formulario interactivo.
2. Si el usuario modifica valores en el formulario y hace clic en *"Aprobar"* en la tarjeta del chat, el frontend adjunta el estado actual del formulario en la propiedad `modeloModificado` del payload `DecisionAprobacionRequest`.
3. `AprobacionHumanaService.decidir(...)` deserializa automáticamente el JSON entrante al subtipo correspondiente (`ModeloLP`, `ModeloInventario`, `ModeloTransporte`, etc.) usando `MetodoResolucion.claseModeloPorMetodo(...)` y **reemplaza el modelo temporal de la solicitud**.
4. El solver se ejecuta sobre el modelo visible/editado por el estudiante.

---

## 3. Bus de Datos y Respuestas Estructuradas (`ChatContextStore`)

Las herramientas invocadas por los subagentes comunican artefactos visuales y resultados al frontend escribiendo en `ChatContextStore` (gestor `ThreadLocal`). Al finalizar el turno, `AiChatController` construye el objeto `ChatResponse` con:
- `respuesta`: texto explicativo en formato Markdown / Socrático.
- `modeloSugerido`: modelo estructurado para rellenar el formulario interactivo.
- `validacion`: feedback pedagógico sobre errores del estudiante.
- `solicitudAprobacion`: tarjeta HITL pendiente de decisión (`solicitudId`, `metodo`, `creadaEn`).
- `resultado` / `resultadoGrafico` / `resultadoTransporte` / `resultadoRedes` / `resultadoEntera` / `resultadoInventario` / `resultadoDinamica`: solución estructurada renderizable en tablas, gráficos o diagramas de red.
