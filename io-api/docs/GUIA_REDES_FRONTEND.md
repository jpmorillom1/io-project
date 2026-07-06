# Guía para implementar el FRONTEND del módulo REDES (`io-ui/`)

> **Para una sesión futura.** El backend de Redes ya está completo (dominio → REST → IA/HITL;
> ver `GUIA_REDES.md` y `API_CONTRACT.md`). Esta guía describe cómo construir la UI **calcada
> de cómo se hizo el frontend de Transporte**: mismos patrones, mismos touch-points, reusando
> `components/shared/NetworkGraph.tsx`. Rutas de archivo relativas a `io-ui/src/`.

---

## 0. Contrato JSON real que produce el backend

### Modelo (entrada) — `ModeloRed`

```jsonc
// Métodos de grafo (DIJKSTRA / KRUSKAL / EDMONDS_KARP / FLUJO_COSTO_MINIMO):
{
  "nodos": ["A", "B", "C", "D"],
  "aristas": [
    // peso → DIJKSTRA/KRUSKAL · capacidad → EDMONDS_KARP/MCF · costo → MCF
    { "origen": "A", "destino": "B", "peso": 4, "capacidad": null, "costo": null }
  ],
  "dirigido": true,          // KRUSKAL lo ignora (siempre no dirigido)
  "metodo": "DIJKSTRA",      // el endpoint REST lo fuerza de todos modos
  "fuente": "A",             // DIJKSTRA (origen) y EK/MCF; null en KRUSKAL
  "sumidero": "D",           // EK/MCF obligatorio; DIJKSTRA opcional (destino de la ruta)
  "agentes": null, "tareas": null, "matrizCostos": null
}

// ASIGNACION (los campos de grafo van null; el backend arma la red bipartita):
{
  "metodo": "ASIGNACION",
  "agentes": ["A1", "A2", "A3"],
  "tareas":  ["T1", "T2", "T3"],
  "matrizCostos": [[9, 2, 7], [6, 4, 3], [5, 8, 1]]
}
```

### Solución — `SolveResult<SolucionRed>` (envoltorio estándar status/solution/steps)

`SolucionRed` es unificada, campos null según el método:

| Campo | DIJKSTRA | KRUSKAL | EDMONDS_KARP | FLUJO_COSTO_MINIMO | ASIGNACION |
|---|---|---|---|---|---|
| `distancias` (map nodo→dist) | ✔ | — | — | — | — |
| `rutaOptima` (lista de nodos) | ✔ (si hubo sumidero) | — | — | — | — |
| `aristasSolucion` (lista de Arista) | ruta/árbol | árbol | arcos con flujo>0 | arcos con flujo>0 | pares elegidos |
| `flujoPorArco` (map `"u->v"`→flujo) | — | — | ✔ | ✔ | ✔ |
| `asignacion` (map agente→tarea) | — | — | — | — | ✔ (sin ficticios) |
| `valorObjetivo` | distancia | peso árbol | flujo máx | costo mínimo | costo mínimo |
| `flujoTotal` | — | — | ✔ | ✔ | ✔ |
| `costoTotal` | — | — | — | ✔ | ✔ |

INFACTIBLE (sumidero inalcanzable, grafo desconexo, sin camino s→t) llega con
`solution: null` y el último paso lo explica — **no** es un error HTTP.

### `steps[].datos` — el grafo de cada paso (¡esto pinta la UI!)

Todos los pasos llevan:

```jsonc
{
  "tipo": "REDES",
  "metodo": "DIJKSTRA",              // submétodo del paso
  "nodos": ["A", "B", "C", "D"],
  "aristas": [                        // TODAS las aristas, con su estado en ESTE paso
    { "origen": "A", "destino": "C", "peso": 2, "estado": "solucion" },
    { "origen": "A", "destino": "B", "peso": 4, "flujo": 3, "estado": "activa" }
  ],
  "fuente": "A", "sumidero": "D"      // si aplican
}
```

`estado` ∈ `normal` (sin marca) · `activa` (examinada/relajada/en el camino de aumento de
este paso) · `solucion` (ya fijada: árbol parcial, arco con flujo) · `descartada` (rechazada
por ciclo en Kruskal). Claves extra por método:

- DIJKSTRA: `nodoActual`, `asentados` (lista), `distancias` (map parcial), y en el paso
  final `rutaOptima` + `distancia`.
- KRUSKAL: `aristaEvaluada` (`"u->v"`), `pesoAcumulado`, y en el paso final `pesoTotal`.
- EDMONDS_KARP: `camino` (lista de nodos), `cuelloBotella`, `flujoTotal`; cada arista trae `flujo`.
- FLUJO_COSTO_MINIMO: lo de EK + `costoUnitario`, `costoAcumulado`, y al final `costoTotal`.
- ASIGNACION: pasos del MCF sobre la red bipartita (nodos `S`, agentes, tareas, `T`) +
  paso 0 con `agentes`/`tareas` y paso final con `asignacion` + `costoTotal`.

---

## 1. Adaptador `lib/redes/construirGrafo.ts` (molde: `lib/transporte/construirGrafo.ts`)

`NetworkGraph` ya es genérico (`GraphNode {id,label,sublabel?,x,y ∈[0,1],variant}` /
`GraphEdge {from,to,label?,value?,active?}`) — **no hay que tocarlo**. Solo escribir el
adaptador. La diferencia clave con Transporte: las aristas **no traen coordenadas**, así que
hay que calcular un **layout automático**:

- **Por defecto — círculo**: nodo i de n en `(0.5 + 0.4·cos θᵢ, 0.5 + 0.4·sin θᵢ)` con
  `θᵢ = 2πi/n − π/2`. Funciona para cualquier grafo (Dijkstra/Kruskal).
- **Opcional — por capas BFS** desde `fuente` (recomendado para EK/MCF): capa = distancia
  BFS; x = capa/(nCapas−1) (fuente 0.06, sumidero 0.94, como `ORIGEN_X`/`DESTINO_X` de
  Transporte); y = índice dentro de la capa con `filaY(idx, n)`.
- **ASIGNACION — bipartito**: idéntico a Transporte (agentes a la izquierda, tareas a la
  derecha, `filaY`); se puede omitir S/T o pintarlos como `source`/`sink` en los extremos.

Funciones a exportar (espejo de las dos de Transporte):

```ts
// Grafo del MODELO: todas las aristas tenues, label = peso / capacidad / "cap (c$)" / costo.
export function grafoModeloRed(modelo: ModeloRed): Grafo

// Grafo de un PASO o de la SOLUCIÓN: lee steps[i].datos (nodos/aristas/estado) —
// estado 'activa'|'solucion' → active: true (+ value: flujo si viene);
// 'descartada' → tenue (o dasheada si se extiende NetworkGraph con una variante).
export function grafoDePaso(datos: StepDatosRed): Grafo
```

Detalles por método al mapear a `GraphEdge`/`GraphNode`:
- `label`: DIJKSTRA/KRUSKAL → peso; EDMONDS_KARP → `flujo/capacidad`; MCF → `flujo/cap ($costo)`;
  ASIGNACION → costo del par.
- `directed`: true en todos **menos KRUSKAL** (`directed={false}` y `curved={false}` —
  aristas rectas estilo árbol); EK/MCF con `curved={true}` (estilo flujo).
- `variant` de nodos: `source` para `fuente`, `sink` para `sumidero`, `active` para nodos
  en `camino`/`rutaOptima`/`nodoActual`, `default` el resto (en ASIGNACION: `origen` para
  agentes, `destino` para tareas, como Transporte).

## 2. Touch-points (en orden; molde = commit del frontend de Transporte)

1. **`types/io.ts`** — añadir: `MetodoRed`, `Arista`, `ModeloRed`, `SolucionRed`,
   `StepDatosRed`, `SolveStepRed`, `SolveResultRed` (espejo del bloque `── Transporte ──`,
   líneas ~198-266). Ampliar: `MetodoResolucion` += `'REDES'` (línea 92),
   `SolicitudAprobacion.modelo` += `ModeloRed` (línea 98), `ChatResponse.resultadoRed:
   SolveResultRed | null` (junto a `resultadoTransporte`, línea 84).
2. **`api/io.ts`** — `resolverRed(modelo: ModeloRed)` con mapa de rutas como
   `RUTA_TRANSPORTE` (línea 52): `DIJKSTRA→'dijkstra'`, `KRUSKAL→'kruskal'`,
   `EDMONDS_KARP→'edmonds-karp'`, `FLUJO_COSTO_MINIMO→'flujo-costo-minimo'`,
   `ASIGNACION→'asignacion'` sobre `${API_BASE}/redes/...`.
3. **`store/useWorkspaceStore.ts`** — `modeloRed`, `modeloRedGrafico` (para el toggle
   Graficar), `resultadoRed` + setters, y añadir `resultadoRed: null` a `resetResultado`
   (línea 69), espejo exacto de las líneas 18-20/51-53/65-67.
4. **`context/ChatProvider.tsx`** — ampliar `moduloDeRespuesta()` (línea 26):
   `if (res.resultadoRed) return 'redes'` y
   `if (res.solicitudAprobacion?.metodo === 'REDES') return 'redes'` **antes** de las ramas
   LP. En `procesarRespuesta()`: bloque `if (res.resultadoRed)` (espejo de línea 84) y
   reflejar el modelo al editor cuando `solicitudAprobacion.metodo === 'REDES'` (espejo de
   línea 90). El chat vive en `AppShell` y sobrevive a la navegación — no tocar nada más:
   `irAModulo('redes')` ya navega a `/redes`.
5. **`components/chat/ApprovalCard.tsx`** — rama `REDES` (espejo de `esTransporte`,
   líneas 102-106): etiqueta `Redes · <submétodo>` con un
   `METODO_RED_LABEL: Record<MetodoRed, string>`, y un resumen del modelo: para grafo,
   lista corta de aristas (o mini-tabla origen/destino/valor); para ASIGNACION, la matriz
   de costos (reusar el patrón `MatrizTransporte`, línea 52).
6. **`components/redes/`** — espejo de `components/transporte/`:
   - `RedModelEditor.tsx` (≈ TransporteModelEditor): selector de método; para grafo,
     editor de nodos (chips) + tabla de aristas (origen/destino/peso/capacidad/costo —
     mostrar solo las columnas del método) + fuente/sumidero; para ASIGNACION, matriz
     agentes×tareas. Botón **Graficar** → `setModeloRedGrafico`.
   - `RedResultViewer.tsx` (≈ TransporteResultViewer): navegación de pasos
     (`useTableauNavigation`), título/descripcion del paso y el grafo del paso.
   - `RedGrafo.tsx` (≈ TransporteGrafo): `NetworkGraph` + toggle **Modelo / Resolución**
     (modelo = `grafoModeloRed(modeloRedGrafico)`; resolución = `grafoDePaso` del paso
     actual o del último).
   - `RedResultBanner.tsx` (≈ TransporteResultBanner): por método —
     distancia/ruta · peso del árbol · flujo máximo · flujo+costo · costo+pares asignados;
     mensaje claro para INFACTIBLE (desconexo/inalcanzable/sin camino).
   - Hooks espejo: `hooks/redes/useResolverRed.ts`, `useRedForm.ts`.
7. **`pages/redes/RedesWorkspace.tsx`** (≈ TransporteWorkspace) y **`App.tsx`**: añadir
   `<Route path="/redes" element={<RedesWorkspace />} />` (junto a línea 12).
8. **`ModuleRail.tsx`** — línea 15: `{ id: 'redes', ..., disponible: true }` (ícono
   `Network` ya está elegido).

## 3. Verificación

1. `npm run build` limpio.
2. REST directo: resolver desde el editor cada método con los ejemplos de
   `API_CONTRACT.md` (Dijkstra distancia 10; Kruskal peso 7; EK flujo 5; MCF costo 7;
   Asignación costo 9) y navegar los pasos viendo el grafo cambiar de estado.
3. **Flujo adaptativo**: en `/lp`, pedir al chat "ruta más corta de A a E..." → el tutor
   pide aprobar (ApprovalCard con `Redes · Dijkstra`) y la app salta a `/redes`; Aprobar →
   `resultadoRed` llega y el grafo muestra la ruta resaltada; Rechazar con comentario →
   el tutor propone corrección sin resolver.
4. Toggle Modelo/Resolución en `RedGrafo` y botón Graficar desde el editor.

## 4. Gotchas

- **INFACTIBLE no es error**: `solution === null` con status `INFACTIBLE` → banner
  explicativo, no toast de error. El último paso trae la explicación.
- Los `datos.aristas` de cada paso ya vienen **con estado calculado** — no recalcular en
  el frontend; solo mapear estado→estilo.
- En ASIGNACION los pasos incluyen los nodos internos `S`/`T` y el posible `Ficticio`:
  pintarlos (`source`/`sink`; `Ficticio` en `default` gris, como el `Ficticio` de Transporte).
- `flujoPorArco` usa claves `"origen->destino"` (flecha ASCII `->`).
- KRUSKAL: no enviar `fuente`/`sumidero`; el backend valida y responde 400 con mensaje en
  español si el modelo está malformado (mostrarlo tal cual, como hace Transporte).
