# Módulo Programación Dinámica (determinística) — COMPLETADO (backend)

Frontend pendiente. El backend cubre dominio → aplicación → REST → capa de IA (tools + HITL).

## Naturaleza

A diferencia de Inventarios (fórmulas cerradas) o de LP (un algoritmo único sobre un modelo único),
la Programación Dinámica **no es un algoritmo, es una forma de descomponer**: el problema se corta en
**etapas** encadenadas, cada etapa se resuelve conociendo ya el valor óptimo de las siguientes, y el
resultado se recompone hacia adelante.

Por eso los cinco submodelos comparten la misma mecánica —**recursión hacia atrás**— y la misma forma
de salida, aunque sus datos de entrada no se parezcan en nada. Un modelo de PD debe contener siete
cosas, y `SolucionDinamica` tiene un campo para cada una:

| Elemento del modelo | Campo de `SolucionDinamica` |
|---|---|
| Etapas | `definicionEtapas` |
| Estados | `definicionEstados` |
| Decisiones | `definicionDecisiones` |
| Función de recurrencia | `funcionRecurrencia` (+ `TablaEtapa.recurrencia`, instanciada por etapa) |
| Tabla de solución | `tablas` — `List<TablaEtapa>` |
| Principio de optimalidad | `principioOptimalidad` (`DinamicaUtils.PRINCIPIO_OPTIMALIDAD`) |
| Interpretación de la política óptima | `politicaOptima` + `interpretacionPolitica` |

Más `valorOptimo` y, solo en `RUTA_ETAPAS`, `rutaOptima`.

Estructura de una tabla:

```
TablaEtapa(etapa, nombreEtapa, recurrencia, filas)
  └── FilaEtapa(estado, evaluaciones, decisionOptima, valorOptimo)
        └── EvaluacionDecision(decision, contribucion, valorFuturo, valorTotal, optima)
```

`valorTotal = contribucion + valorFuturo`. Ese desglose es el corazón pedagógico del módulo: deja ver
que el óptimo de un estado no es la mejor contribución inmediata, sino la mejor **suma** de
contribución inmediata y valor futuro ya optimizado.

### Infactibilidad e infinitos

`RUTA_ETAPAS` y `PLANIFICACION_PRODUCCION` **sí pueden ser infactibles** (origen sin camino al
destino; capacidad insuficiente para cubrir la demanda). Como manda el contrato: eso es
`SolveStatus.INFACTIBLE` con `solution = null`, nunca una excepción. Las excepciones se reservan
para entradas malformadas (`DinamicaValidador` → HTTP 400).

> ⚠ Internamente los estados inalcanzables valen ±∞. **Nunca llegan a la salida**: los solvers omiten
> esos estados de `TablaEtapa.filas`. Jackson serializaría un infinito como el token `Infinity`, que
> no es JSON válido y rompería cualquier cliente. `DinamicaControllerTest` lo verifica reparseando la
> respuesta.

## Submodelos y recurrencias

Todos usan `SentidoOptimizacion`; los tres de sentido fijo rechazan el sentido contrario en el
validador en vez de ignorarlo en silencio.

### 1. `ASIGNACION_RECURSOS` — asignación de recursos, distribución de presupuesto

Repartir `recursoTotal` unidades enteras entre `n` actividades, cada una con una **tabla** de retorno
`r_i(x)` (no una fórmula lineal — si lo fuera, bastaría PL).

- Etapa `i` = actividad `i` · Estado `s` = recurso disponible al llegar · Decisión `x` = cuánto asignar
- `f_i(s) = opt{ r_i(x) + f_(i+1)(s − x) : 0 ≤ x ≤ s }`, con `f_(n+1)(s) = 0`
- Sentido MAXIMIZAR por defecto; MINIMIZAR permitido (tablas de costo). Nunca infactible (`x = 0` siempre cabe).

### 2. `MOCHILA` — selección de inversiones o proyectos

- Etapa `i` = artículo `i` · Estado `s` = capacidad libre · Decisión `x` = unidades cargadas
- `f_i(s) = max{ v_i·x + f_(i+1)(s − p_i·x) : 0 ≤ x ≤ min(u_i, ⌊s / p_i⌋) }`, con `f_(n+1)(s) = 0`
- `unidadesMaximas` (`u_i`) omitido ⇒ 1, es decir la mochila 0/1 clásica. Sentido MAXIMIZAR fijo. Nunca infactible.

### 3. `RUTA_ETAPAS` — rutas secuenciales (problema de la diligencia)

- Etapa `k` = columna `k` de la red · Estado `s` = nodo actual · Decisión = a qué nodo de la etapa `k+1` saltar
- `f_k(s) = opt{ c(s,d) + f_(k+1)(d) : existe arco s → d }`, con `f_K(destino) = 0`
- Sentido MINIMIZAR por defecto. La etapa 1 tiene exactamente un nodo (el origen); cada arco avanza
  exactamente una etapa; los nombres de nodo son únicos. **Puede ser INFACTIBLE.**
- Si el grafo no está por etapas, el problema es de Redes (Dijkstra), no de PD.

### 4. `PLANIFICACION_PRODUCCION` — producción e inventarios por etapas

- Etapa `t` = periodo `t` · Estado `i` = inventario al **inicio** del periodo · Decisión `x` = cuánto producir
- Inventario al cierre del periodo: `j = i + x − d_t`, con `0 ≤ j ≤ capacidadAlmacen` (sin faltantes)
- `f_t(i) = min{ K·[x > 0] + c·x + h·j + f_(t+1)(j) }`
- Frontera: `f_(T+1)(i) = 0` si `i == inventarioFinal`, `∞` en otro caso
- Sentido MINIMIZAR fijo. **Puede ser INFACTIBLE.** Si `capacidadAlmacen` es null se acota por la demanda
  total más el inventario final; si `capacidadProduccion` es null, igual.
- El solver propaga primero los estados **alcanzables hacia adelante** desde `inventarioInicial`, así las
  tablas quedan pequeñas y legibles en vez de enumerar todo el rango de inventarios.

### 5. `REEMPLAZO_EQUIPOS` — reemplazo de equipos

- Etapa `t` = año `t` · Estado `e` = edad del equipo al inicio del año · Decisión = CONSERVAR o REEMPLAZAR
- `f_t(e) = max{ CONSERVAR: r(e) − c(e) + f_(t+1)(e+1) ; REEMPLAZAR: s(e) − I + r(0) − c(0) + f_(t+1)(1) }`
- Frontera: `f_(n+1)(e) = s(e)` — al cerrar el horizonte el equipo se vende por su valor de rescate
- Cuando `e == edadMaxima`, CONSERVAR deja de ser admisible. Sentido MAXIMIZAR fijo. Nunca infactible
  (REEMPLAZAR siempre está disponible). `tablaEdades` debe cubrir todas las edades `0..edadMaxima`.

## Endpoints

Cinco endpoints, uno por submodelo. Todos aceptan el mismo body `ModeloDinamico` (cada endpoint fuerza
su método con `conMetodo(...)`) y devuelven `SolveResult<SolucionDinamica>`.

```
POST /api/v1/dinamica/asignacion-recursos       → reparto de un recurso entre actividades/periodos
POST /api/v1/dinamica/mochila                   → selección de artículos/proyectos/inversiones
POST /api/v1/dinamica/ruta-etapas               → ruta secuencial sobre una red por etapas
POST /api/v1/dinamica/planificacion-produccion  → producción e inventarios por etapas
POST /api/v1/dinamica/reemplazo-equipos         → conservar o reemplazar cada año
```

**Request** (mochila 0/1):
```json
{
  "capacidad": 5,
  "articulos": [
    { "nombre": "A", "peso": 2, "valor": 3 },
    { "nombre": "B", "peso": 3, "valor": 4 },
    { "nombre": "C", "peso": 4, "valor": 5 }
  ]
}
```

**Response** — `solution` es `SolucionDinamica`; `steps` trae la formulación, una entrada por etapa
(con su `TablaEtapa` en `datos.tabla`) y la recuperación de la política:
```json
{
  "status": "OPTIMO",
  "solution": {
    "valorOptimo": 7.0,
    "tablas": [ { "etapa": 3, "nombreEtapa": "Etapa 3 — C", "recurrencia": "...", "filas": [ ... ] } ],
    "politicaOptima": [ { "etapa": 1, "estadoEntrada": "s = 5", "decision": "x = 1", "estadoSalida": "s = 3" } ],
    "rutaOptima": null,
    "definicionEtapas": "...", "definicionEstados": "...", "definicionDecisiones": "...",
    "funcionRecurrencia": "...", "principioOptimalidad": "...", "interpretacionPolitica": "..."
  },
  "steps": [ { "numero": 1, "titulo": "Formulación del modelo", "datos": { "tipo": "PROGRAMACION_DINAMICA", ... } } ]
}
```

El mapa `datos` de cada paso lleva siempre `tipo: "PROGRAMACION_DINAMICA"` y `metodo`. El paso de
formulación añade `etapas`/`estados`/`decisiones`/`recurrencia`/`principioOptimalidad`; los pasos de
etapa añaden `tabla`; el paso final añade `politica`, `valorOptimo` y, si aplica, `rutaOptima`.

Las tablas se emiten **en el orden de la recursión hacia atrás**: `tablas[0]` es la última etapa.

## Capa de IA (HITL)

`DinamicaTool` expone **una `@Tool` por submodelo** (`resolverPdAsignacionRecursos`, `resolverPdMochila`,
`resolverPdRutaEtapas`, `resolverPdPlanificacionProduccion`, `resolverPdReemplazoEquipos`). Ninguna
resuelve: todas terminan en `SolicitudAprobacionHelper.solicitar(..., MetodoResolucion.PROGRAMACION_DINAMICA)`,
así que el solver solo corre tras el clic humano.

- Las listas de estructuras van como `List<Record>` plano (`ActividadInput`, `ArticuloInput`,
  `EtapaInput`, `ArcoInput`, `EdadInput`), nunca genéricos anidados como parámetro directo.
- Todos los records anidados llevan `@JsonIgnoreProperties(ignoreUnknown = true)`; los componentes
  opcionales (`unidadesMaximas`), `@JsonProperty(required = false)`.
- Los parámetros opcionales van con `@P(value = "... OMITE este campo (nunca null)", required = false)`
  y tipo wrapper.
- `DinamicaToolSchemaTest` verifica que LangChain4j genera los cinco esquemas sin explotar — si fallara,
  el bean `tutorAiService` no arrancaría.

En la cadena HITL: `ResolucionEjecutor.Ejecucion` tiene un séptimo `SolveResult`, `resultadoDinamica`;
`formatearDinamica(...)` produce el resumen que el tutor recibe en el mensaje `[SISTEMA]` (formulación,
valor óptimo, política etapa por etapa, tablas, pasos y guía socrática). `ChatResponse.resultadoDinamica`
lo lleva a la UI.

El system prompt describe la señal de detección (decisiones en etapas encadenadas, retornos en tabla) y
las dos confusiones frecuentes: demanda constante ⇒ EOQ, no PD; grafo sin etapas ⇒ Dijkstra, no ruta por etapas.

## Valores de referencia (para tests)

| Caso | Entrada | Resultado |
|---|---|---|
| Asignación de recursos | R=2; A=[0,4,6], B=[0,3,8] | Z* = 8 con `x_A = 0`, `x_B = 2` |
| Mochila 0/1 | cap. 5; A(2,3), B(3,4), C(4,5) | Z* = 7 llevando A y B |
| Mochila multiunidad | cap. 6; A(2,3) con `unidadesMaximas = 3` | Z* = 9 con `x_A = 3` |
| Ruta por etapas (diligencia) | 5 etapas: A / B,C,D / E,F,G / H,I / J | Z* = 11 por A → C → E → H → J |
| Planificación de producción | d=[3,2,4], K=3, c=1, h=1 | Z* = 17 con plan 5, 0, 4 |
| Producción infactible | igual, `capacidadProduccion = 2` | `INFACTIBLE`, `solution = null` |
| Reemplazo de equipos | horizonte 2, I=10; edad 0:(20,2,8), 1:(18,4,6), 2:(15,8,3) | Z* = 38: conservar, luego reemplazar |
| Reemplazo a la edad máxima | horizonte 1, `edadInicial = edadMaxima = 2` | Z* = 17, única decisión REEMPLAZAR |

Detalle del caso de producción (el costo de producción, 9, es fijo; solo compiten preparaciones e
inventario): `3-2-4` → 18 · **`5-0-4` → 17** · `3-6-0` → 19 · `9-0-0` → 22.

Detalle de la diligencia: `f₄(H)=3, f₄(I)=4`; `f₃(E)=4, f₃(F)=7, f₃(G)=6`; `f₂(B)=11, f₂(C)=7, f₂(D)=8`;
`f₁(A) = min(2+11, 4+7, 3+8) = 11`.
