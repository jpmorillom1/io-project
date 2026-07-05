# Guía para implementar el módulo REDES

> Guía de arranque para la próxima sesión. El módulo **Redes** se implementa igual que
> **Transporte** (ya completo): reusa el mismo patrón dominio → aplicación → REST → tool IA
> con HITL, y **reusa el `NetworkGraph` genérico** para la visualización. Lee primero
> `docs/TRANSPORTE.md` y `CLAUDE.md §10`; esta guía solo marca las diferencias.

---

## 0. Qué se va a construir (spec — `MODULOS_IO.md §3`)

Tres algoritmos sobre un grafo dado por el usuario:

| Algoritmo | Entrada | Salida | Pasos |
|---|---|---|---|
| **Dijkstra** | grafo dirigido/no dirigido, pesos ≥ 0, nodo fuente (+ destino opcional) | distancias y predecesores; ruta a un destino | nodo extraído + relajaciones por iteración |
| **Kruskal** (+ Union-Find) | grafo **no dirigido conexo**, pesos | árbol de expansión mínima (MST) | cada arista aceptada/rechazada + peso acumulado |
| **Edmonds-Karp** | red dirigida con capacidades, fuente + sumidero | flujo máximo y flujo por arco | cada camino de aumento, su cuello de botella y el residual |

Validación: un grafo pequeño con resultado conocido para cada uno.

Pesos negativos en Dijkstra → `IllegalArgumentException` (regla del proyecto: solo entradas
malformadas lanzan; "infactible/desconexo" es un `SolveStatus`, no una excepción).

---

## 1. Backend — dominio (`domain/redes/`, Java puro)

Sigue el molde de `domain/transporte/`.

```
domain/redes/
  ModeloRed.java          record(nodos, aristas, dirigido, metodo, fuente, destino?)  implements ModeloResoluble
  Arista.java             record(origen, destino, peso, capacidad?)   // peso para Dijkstra/Kruskal, capacidad para flujo
  SolucionRed.java        record(...)   // ver abajo
  MetodoRed.java          enum: DIJKSTRA, KRUSKAL, EDMONDS_KARP
  dijkstra/DijkstraSolver.java
  kruskal/{KruskalSolver.java, UnionFind.java}
  edmondskarp/EdmondsKarpSolver.java
  RedUtils.java           // (opcional) helpers de pasos, igual que TransporteUtils
```

- `ModeloRed implements ModeloResoluble` (la interfaz marcador de `domain/common/`) — **imprescindible**
  para que entre en la cadena HITL sin re-tipar nada.
- `SolucionRed` unificada (un solo record con campos nullable según algoritmo), p.ej.:
  `distancias` (Map nodo→dist, Dijkstra), `rutaOptima` (List<nodo>), `aristasSolucion`
  (List<Arista> — MST de Kruskal o arcos con flujo>0), `valorObjetivo` (dist total / peso MST /
  flujo máximo), `flujoPorArco` (Map). Uno por algoritmo; los demás null.
- Cada solver: `resolver(ModeloRed) → SolveResult<SolucionRed>`, emite `SolveStep` con
  `datos` en `LinkedHashMap` (mismo estilo que `TransporteUtils.datosBase`). Nunca lanza por
  grafo desconexo → `SolveStatus.INFACTIBLE`; solo `validar(...)` lanza.
- **Clave para el frontend**: en `datos` de cada paso incluye lo necesario para pintar el grafo
  (nodos, aristas, y marca de arista activa/relajada/en el árbol/en el camino de aumento).

## 2. Aplicación + REST (idéntico a transporte)

```
application/redes/
  RedUseCase.java         interfaz: resolver(ModeloRed) → SolveResult<SolucionRed>
  RedService.java         @Service; switch(modelo.metodo()) → solver (solvers creados con `new`)

infrastructure/redes/
  RedController.java       @RestController @RequestMapping("/api/v1/redes")
                           POST /dijkstra, /kruskal, /edmonds-karp  (cada uno fuerza su método)
```
Bind directo de `ModeloRed` como `@RequestBody`, devuelve `ResponseEntity<SolveResult<SolucionRed>>`.
Sin DTOs. `GlobalExceptionHandler` ya mapea `IllegalArgumentException → 400`.

## 3. Capa IA / HITL — checklist de touch-points

La cadena HITL ya es genérica (`ModeloResoluble`). Solo hay que:

1. `hitl/MetodoResolucion` → agregar **`REDES`** (un solo valor; el submétodo va en `ModeloRed.metodo`,
   igual que Transporte).
2. `hitl/ResolucionEjecutor`:
   - inyectar `RedUseCase`;
   - extender el record `Ejecucion` con `SolveResult<SolucionRed> resultadoRed`;
   - `if (metodo == REDES) { ModeloRed mr = (ModeloRed) modelo; ... return new Ejecucion(null,null,null,resultado, formatearRed(...)); }`
   - agregar `formatearRed(...)` (resumen textual para el tutor);
   - `case REDES -> throw` en los `switch` exhaustivos LP tabulares.
3. `ChatContextStore.DatosRespuesta` → `public SolveResult<SolucionRed> resultadoRed;`
4. `dto/ChatResponse` → componente `SolveResult<SolucionRed> resultadoRed`.
5. `AiChatController` → pasar `resultadoRed` en las **4** construcciones de `ChatResponse`
   (`/chat` try+catch, `/chat/aprobacion` try+catch).
6. `infrastructure/ai/tools/RedTool` (copia `TransporteTool`):
   - `@Tool resolverRed(...)` construye `ModeloRed` y llama
     `SolicitudAprobacionHelper.solicitar(..., MetodoResolucion.REDES)`.
   - ⚠️ **GOTCHA de LangChain4j 1.13.0**: NO declares parámetros con genéricos anidados
     (`List<List<...>>`) ni tipos complejos crudos. Las **aristas** van como
     `List<AristaInput>` donde `AristaInput` es un `record(String origen, String destino, double peso, double capacidad)`
     — exactamente como `TransporteTool.FilaCostos`. Si no, el bean `tutorAiService` **no arranca**
     (`ParameterizedTypeImpl cannot be cast to Class`).
7. `AiConfig.tutorAiService(...)` → inyectar `RedTool` y añadirlo a `.tools(...)`.
8. `resources/prompts/tutor_system_prompt.txt` → nueva herramienta 8; cómo reconocer un
   problema de redes; mover Redes de "EN DESARROLLO" a "DISPONIBLE".

## 4. Tests (`src/test/.../domain/redes/`)

JUnit 5, aserciones planas, solver via `new` (molde `ModiSolverTest`):
- Un test por solver con **grafo de resultado conocido** (ruta mínima, peso MST, flujo máximo).
- Kruskal: caso con ciclo (arista rechazada) y grafo desconexo → INFACTIBLE (sin MST completo).
- Dijkstra: peso negativo → `assertThrows(IllegalArgumentException)`.
- `RedControllerTest` (molde `TransporteControllerTest`): controller + serialización Jackson
  (¡ojo con `int[]`/estructuras en `datos`!).
- Añadir un caso REDES a `ResolucionAprobadaWorkflowTest` (HITL end-to-end devuelve `resultadoRed`).

---

## 5. Frontend (`io-ui/`) — reusa NetworkGraph y el patrón adaptativo

### 5.1 La gran ventaja: `components/shared/NetworkGraph.tsx` ya sirve
Redes **es** un grafo general, así que el visor ya existe. Solo escribe el adaptador
`lib/redes/construirGrafo.ts` (molde `lib/transporte/construirGrafo.ts`):
- Nodos con posición `x,y ∈ [0,1]`. Para grafos generales necesitas **posicionar los nodos**:
  o vienen con coordenadas del usuario, o aplicas un layout simple (círculo, o capas por
  distancia/nivel BFS). Para empezar: layout en círculo `(0.5+0.4cosθ, 0.5+0.4sinθ)`.
- Aristas: `label` = peso/capacidad; `active` = está en la ruta / MST / camino de aumento;
  `value` = flujo (para flujo máximo).
- Pasa `directed` según el grafo. Para **Kruskal (no dirigido)** usa `directed={false}` y
  probablemente `curved={false}` (las rectas leen mejor para un MST). Estas props ya existen.
- Variantes de nodo disponibles: `source` (naranja) y `sink` (rojo) para fuente/sumidero de
  Edmonds-Karp; `active` (verde) para nodos en la ruta; `default` para el resto.

`NetworkGraph` ya resuelve: flechas de tamaño fijo (`markerUnits=userSpaceOnUse`), etiquetas de
nodo truncadas con `…` + `<title>`, carriles/encabezados opcionales, aristas curvas o rectas.
No lo modifiques salvo que necesites algo nuevo (si lo haces, que siga siendo genérico).

### 5.2 Touch-points de UI (molde Transporte)
- `types/io.ts`: `MetodoRed`, `ModeloRed`, `Arista`, `SolucionRed`, `SolveResultRed`; extender
  `MetodoResolucion` con `'REDES'`; `SolicitudAprobacion.modelo` → añadir `| ModeloRed`;
  `ChatResponse` → `resultadoRed: SolveResultRed | null`.
- `api/io.ts`: `resolverRed(modelo)` (POST según `modelo.metodo`).
- `store/useWorkspaceStore.ts`: `modeloRed`, `modeloRedGrafico`, `resultadoRed` + setters;
  incluir en `resetResultado`.
- `context/ChatProvider.tsx`: en `procesarRespuesta` setear `resultadoRed`; y en
  **`moduloDeRespuesta()`** añadir la rama Redes (por `resultadoRed` o
  `solicitudAprobacion.metodo === 'REDES'`) → auto-navega a `/redes`. **Este es el punto que
  hace el workspace adaptativo** (el chat es un contexto único montado en `AppShell`, sobrevive
  a la navegación — no lo dupliques).
- `components/chat/ApprovalCard.tsx`: ramifica por `metodo === 'REDES'` para pintar el grafo/aristas
  (o una lista de aristas) en la tarjeta de aprobación.
- `components/redes/`: `RedModelEditor` (molde `TransporteModelEditor` — inputs de aristas + selector
  de método + botón Graficar + Resolver), `RedResultViewer` (molde `TransporteResultViewer` —
  Card con navegación de pasos + banner), `RedGrafo` (molde `TransporteGrafo` — toggle Modelo/Resolución
  usando `NetworkGraph`).
- `pages/redes/RedesWorkspace.tsx` (molde `TransporteWorkspace`): idle + chat izq + cards der.
- `App.tsx`: `<Route path="/redes" element={<RedesWorkspace />} />`.
- `components/layout/ModuleRail.tsx`: `redes` → `disponible: true`.

### 5.3 Verificación
`npm run build` sin errores de tipos. Prueba el flujo adaptativo: pide un problema de redes en
`/lp` → debe saltar a `/redes`; Graficar muestra el grafo; Resolver lo actualiza a la solución
(ruta/MST/flujo) en el mismo componente.

---

## 6. Resumen de "no repetir errores"
- ✅ `ModeloRed implements ModeloResoluble` — no re-tipes la cadena HITL.
- ✅ Un solo valor `MetodoResolucion.REDES`; el submétodo dentro del modelo.
- ⚠️ `@Tool`: aristas como `List<AristaInput>` (record), **nunca** genéricos anidados.
- ✅ Reusa `NetworkGraph` + `construirGrafo` adapter; no reinventes el SVG.
- ✅ Chat adaptativo = ampliar `moduloDeRespuesta()` en `ChatProvider`.
- ✅ Backend verificable sin Groq/DB con tests de dominio + `*ControllerTest` (Jackson) +
  caso REDES en `ResolucionAprobadaWorkflowTest`. (`contextLoads()` falla sin `GROQ_API_KEY` — pre-existente.)
