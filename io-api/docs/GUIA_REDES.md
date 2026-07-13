# Guía para implementar el módulo REDES (backend)

> Guía de arranque. El módulo **Redes** se implementa igual que **Transporte** (ya completo):
> reusa el patrón dominio → aplicación → REST → tool IA con HITL. Lee primero
> `docs/TRANSPORTE.md` y `CLAUDE.md §10`; esta guía marca las diferencias.
>
> **Alcance de esta sesión = SOLO BACKEND** (dominio → aplicación → REST → capa IA / chat).
> El frontend se implementa en **otra sesión**: aquí solo se deja escrito
> `docs/GUIA_REDES_FRONTEND.md` (ver §5).

---

## 0. Qué se va a construir — 5 problemas de redes

| # | Problema | Algoritmo | Notas |
|---|---|---|---|
| 1 | **Ruta más corta** | Dijkstra | pesos ≥ 0 (negativos → `IllegalArgumentException`) |
| 2 | **Árbol de expansión mínima** | Kruskal (+ Union-Find) | grafo **no dirigido conexo** |
| 3 | **Flujo máximo** | Edmonds-Karp | BFS de caminos de aumento sobre la red residual |
| 4 | **Flujo de costo mínimo** | Successive Shortest Paths (Bellman-Ford) | **versión simple: min-cost max-flow s→t** (ver abajo) |
| 5 | **Asignación (vía red)** | reducción a flujo de costo mínimo | bipartito unitario; reusa el core de MCF |

**Idea clave:** el flujo de costo mínimo (MCF) es el problema "madre" — ruta más corta,
flujo máximo, transporte y asignación son casos suyos. Aquí MCF se hace en su forma
**más simple** y la asignación se **reduce** a MCF (no se implementa Húngaro).

### Flujo de costo mínimo — versión simple (min-cost max-flow s→t)
NO se modelan ofertas/demandas por nodo. Entrada: red dirigida con `capacidad` y `costo`
por arco, una **fuente s** y un **sumidero t**. Se calcula el **flujo máximo de costo
mínimo** de s a t por *successive shortest paths*:
1. Red residual: cada arco directo (cap, costo); su arco inverso (cap 0, costo −costo).
2. Repetir: hallar el camino de **menor costo** s→t en el residual (sobre arcos con
   capacidad residual > 0) con **Bellman-Ford/SPFA** (maneja los costos negativos de los
   inversos; no hacen falta potenciales). Si no hay camino → fin.
3. Aumentar por el cuello de botella; acumular costo. **steps:** cada camino, su costo,
   su cuello de botella y el residual actualizado.
4. Salida: flujo máximo, costo total mínimo, flujo por arco.

### Asignación (vía red)
n agentes, m tareas, matriz de costos `c`. Se construye la red bipartita y se resuelve
con el core de MCF:
`S → agente_i` (cap 1, costo 0) · `agente_i → tarea_j` (cap 1, costo cᵢⱼ) · `tarea_j → T`
(cap 1, costo 0). El flujo de costo mínimo de valor min(n,m) da la asignación óptima
(los arcos agente→tarea con flujo 1). Balancear con agente/tarea ficticio si n ≠ m.
> Alternativa válida: reusar el módulo **Transporte** (matriz n×n con oferta=demanda=1).
> Recomendado hacerlo sobre MCF para mantenerlo dentro de Redes.

Validación: un grafo pequeño con resultado conocido para cada uno de los 5.
Regla del proyecto: "grafo desconexo / sin factibilidad" es un `SolveStatus.INFACTIBLE`,
no una excepción; solo entradas malformadas lanzan `IllegalArgumentException`.

---

## 1. Dominio (`domain/redes/`, Java puro)

Molde: `domain/transporte/`.

```
domain/redes/
  ModeloRed.java          record(nodos, aristas, dirigido, metodo, fuente?, sumidero?)  implements ModeloResoluble
  Arista.java             record(origen, destino, peso, capacidad, costo)   // se usan según el método
  SolucionRed.java        record unificada (campos nullable por método) — ver abajo
  MetodoRed.java          enum: DIJKSTRA, KRUSKAL, EDMONDS_KARP, FLUJO_COSTO_MINIMO, ASIGNACION
  dijkstra/DijkstraSolver.java
  kruskal/{KruskalSolver.java, UnionFind.java}
  edmondskarp/EdmondsKarpSolver.java
  flujocostominimo/FlujoCostoMinimoSolver.java     // core reusable (Bellman-Ford SSP)
  asignacion/AsignacionSolver.java                 // arma la red bipartita y delega en el core de MCF
  RedUtils.java           // helpers de pasos (molde TransporteUtils)
```

- `Arista` lleva `peso` (Dijkstra/Kruskal), `capacidad` (Edmonds-Karp/MCF) y `costo` (MCF).
  Para **asignación** la entrada natural es una **matriz de costos** + etiquetas
  agente/tarea; decide si va como campo aparte en `ModeloRed` (p.ej. `matrizCostos`,
  `agentes`, `tareas`, nullable) o como aristas bipartitas `agente→tarea` con `costo`.
- `ModeloRed implements ModeloResoluble` (interfaz marcador de `domain/common/`) — **imprescindible**
  para entrar en la cadena HITL sin re-tipar nada.
- `SolucionRed` unificada, campos nullable según algoritmo:
  - `distancias: Map<String,Double>` y `rutaOptima: List<String>` (Dijkstra)
  - `aristasSolucion: List<Arista>` (MST de Kruskal, o arcos con flujo>0)
  - `flujoPorArco: Map<String,Double>` (Edmonds-Karp / MCF)
  - `asignacion: Map<String,String>` (agente→tarea) (Asignación)
  - `valorObjetivo: double` (dist. a destino / peso MST / flujo máx / costo mín / costo asignación)
  - `costoTotal: double` (MCF / asignación, cuando aplica)
- Cada solver: `resolver(ModeloRed) → SolveResult<SolucionRed>`, emite `SolveStep` con
  `datos` en `LinkedHashMap` (estilo `TransporteUtils.datosBase`). En `datos` incluye lo
  necesario para pintar el grafo del paso (nodos, aristas, marca de arista activa/relajada/
  en el árbol/en el camino de aumento) — lo consumirá el frontend.
- El **core de MCF** (`FlujoCostoMinimoSolver`) debe ser reusable por `AsignacionSolver`
  (expón un método interno que reciba la red construida y devuelva flujo/costo/pasos).

## 2. Aplicación + REST (idéntico a transporte)

```
application/redes/
  RedUseCase.java   interfaz: resolver(ModeloRed) → SolveResult<SolucionRed>
  RedService.java   @Service; switch(modelo.metodo()) → solver (creados con `new`)

infrastructure/redes/
  RedController.java  @RestController @RequestMapping("/api/v1/redes")
    POST /dijkstra, /kruskal, /edmonds-karp, /flujo-costo-minimo, /asignacion
    (cada endpoint fuerza su método)
```
Bind directo de `ModeloRed` como `@RequestBody`; devuelve `ResponseEntity<SolveResult<SolucionRed>>`.
Sin DTOs. `GlobalExceptionHandler` ya mapea `IllegalArgumentException → 400`.

## 3. Capa IA / HITL — checklist de touch-points

La cadena HITL ya es genérica (`ModeloResoluble`). Solo hay que:

1. `hitl/MetodoResolucion` → agregar **`REDES`** (un solo valor; el submétodo va en
   `ModeloRed.metodo`, igual que Transporte).
2. `hitl/ResolucionEjecutor`:
   - inyectar `RedUseCase`;
   - extender el record `Ejecucion` con `SolveResult<SolucionRed> resultadoRed`;
   - `if (metodo == REDES) { ModeloRed mr = (ModeloRed) modelo; ... return new Ejecucion(null,null,null,resultado, formatearRed(...)); }`
   - `formatearRed(...)` (resumen textual para el tutor, por método);
   - `case REDES -> throw` en los `switch` exhaustivos LP tabulares.
3. `ChatContextStore.DatosRespuesta` → `public SolveResult<SolucionRed> resultadoRed;`
4. `dto/ChatResponse` → componente `SolveResult<SolucionRed> resultadoRed`.
5. `AiChatController` → pasar `resultadoRed` en las **4** construcciones de `ChatResponse`
   (`/chat` try+catch, `/chat/aprobacion` try+catch).
6. `infrastructure/ai/tools/RedTool` (copia `TransporteTool`):
   - `@Tool resolverRed(...)` construye `ModeloRed` y llama
     `SolicitudAprobacionHelper.solicitar(..., MetodoResolucion.REDES)`.
   - ⚠️ **GOTCHA LangChain4j 1.13.0**: NO declares parámetros `@Tool` con genéricos anidados
     (`List<List<...>>`). Las **aristas** van como `List<AristaInput>` donde `AristaInput` es un
     `record(String origen, String destino, double peso, double capacidad, double costo)`
     — como `TransporteTool.FilaCostos`. Si no, el bean `tutorAiService` **no arranca**
     (`ParameterizedTypeImpl cannot be cast to Class`).
7. `AiConfig.tutorAiService(...)` → inyectar `RedTool` y añadirlo a `.tools(...)`.
8. `resources/prompts/tutor_system_prompt.txt` → nueva herramienta 8; cómo reconocer cada
   uno de los 5 problemas de redes; mover Redes de "EN DESARROLLO" a "DISPONIBLE".

## 4. Tests (`src/test/.../domain/redes/`)

JUnit 5, aserciones planas, solver via `new` (molde `ModiSolverTest`):
- Un test por solver con **grafo de resultado conocido** (ruta mínima, peso MST, flujo máximo,
  costo mínimo, costo de asignación óptima).
- Kruskal: caso con ciclo (arista rechazada) y grafo desconexo → `INFACTIBLE`.
- Dijkstra: peso negativo → `assertThrows(IllegalArgumentException)`.
- MCF: red con arco inverso ejercitado (que el SSP re-rutee) → costo mínimo correcto.
- Asignación: matriz pequeña con óptimo conocido; caso n≠m (balanceo).
- `RedControllerTest` (molde `TransporteControllerTest`): controller + serialización Jackson.
- Añadir un caso REDES a `ResolucionAprobadaWorkflowTest` (HITL end-to-end devuelve `resultadoRed`).

Verifica: `gradle test --tests "*redes*"` y `gradle compileJava`. (`contextLoads()` falla sin
`GROQ_API_KEY` — es pre-existente, ignóralo.)

---

## 5. Frontend — SE IMPLEMENTA EN OTRA SESIÓN

**No construyas el frontend en la sesión de backend.** En su lugar, el entregable extra de
esa sesión es escribir **`docs/GUIA_REDES_FRONTEND.md`**: una guía paso a paso para que una
sesión futura implemente la UI, calcada de cómo se hizo el frontend de Transporte.

Esa guía de frontend debe cubrir, como mínimo:

- **Reusar `components/shared/NetworkGraph.tsx`** (SVG genérico ya existente). Redes es un grafo
  general, así que el visor ya sirve. Solo hay que escribir el adaptador `lib/redes/construirGrafo.ts`
  (molde `lib/transporte/construirGrafo.ts`):
  - **Posición de nodos:** las aristas no traen coordenadas → aplicar un **layout automático**
    (por defecto: círculo `(0.5+0.4·cosθ, 0.5+0.4·sinθ)`; opcional por capas BFS desde la fuente).
  - Aristas: `label` = peso/costo/capacidad según método; `active` = en la solución (ruta / MST /
    camino de aumento / arco con flujo); `value` = flujo.
  - `directed` según método (Kruskal `false` y `curved={false}`; los demás dirigidos). Variantes de
    nodo `source`/`sink` (Edmonds-Karp/MCF), `active` para nodos en la ruta.
- **Touch-points de UI** (molde Transporte): `types/io.ts` (MetodoRed, ModeloRed, Arista, SolucionRed,
  SolveResultRed; `MetodoResolucion` += 'REDES'; `SolicitudAprobacion.modelo` += ModeloRed;
  `ChatResponse.resultadoRed`), `api/io.ts` (`resolverRed`), `store` (modeloRed/modeloRedGrafico/
  resultadoRed + reset), `context/ChatProvider.tsx` (**ampliar `moduloDeRespuesta()`** con la rama
  Redes → auto-navega a `/redes`; el chat es un contexto único en AppShell, sobrevive a la navegación),
  `components/chat/ApprovalCard.tsx` (rama REDES), `components/redes/` (RedModelEditor, RedResultViewer,
  RedGrafo con toggle Modelo/Resolución), `pages/redes/RedesWorkspace.tsx`, `App.tsx` (ruta `/redes`),
  `ModuleRail.tsx` (`disponible: true`).
- Verificación: `npm run build` y probar el flujo adaptativo (pedir un problema de redes en `/lp`
  debe saltar a `/redes`; Graficar muestra el grafo; Resolver lo actualiza a la solución).

---

## 6. No repetir errores
- ✅ `ModeloRed implements ModeloResoluble` — no re-tipes la cadena HITL.
- ✅ Un solo valor `MetodoResolucion.REDES`; los 5 submétodos van dentro del modelo (`MetodoRed`).
- ✅ MCF en su forma simple (min-cost max-flow s→t con Bellman-Ford); asignación **reduce** a MCF.
- ⚠️ `@Tool`: aristas como `List<AristaInput>` (record), **nunca** genéricos anidados.
- ✅ Backend verificable sin Groq/DB: tests de dominio + `RedControllerTest` (Jackson) + caso REDES
  en `ResolucionAprobadaWorkflowTest`.
- ✅ El frontend NO se hace aquí: se deja `docs/GUIA_REDES_FRONTEND.md` para otra sesión.
- ✅ No hagas commit ni push salvo que se te pida.
