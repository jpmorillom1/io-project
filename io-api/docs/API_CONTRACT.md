# Contrato de API REST

Base: `http://localhost:8080/api/v1`

Todas las respuestas de resolución comparten la forma de `SolveResult<T>`.

---

## Forma común del resultado (SolveResult)

```json
{
  "status": "OPTIMO",
  "solution": { "...específico del módulo..." },
  "steps": [
    {
      "numero": 0,
      "titulo": "Tableau inicial",
      "descripcion": "Se agregan 2 variables de holgura...",
      "datos": {
        "encabezados": ["x1", "x2", "s1", "s2", "b"],
        "tableau": [[6.0, 4.0, 1.0, 0.0, 24.0], [1.0, 2.0, 0.0, 1.0, 6.0], [-5.0, -4.0, 0.0, 0.0, 0.0]],
        "base": ["s1", "s2"]
      }
    },
    {
      "numero": 1,
      "titulo": "Iteración 1: entra x1, sale s1",
      "descripcion": "Pivote en fila 1, columna x1...",
      "datos": {
        "encabezados": ["x1", "x2", "s1", "s2", "b"],
        "tableau": [[...]],
        "base": ["x1", "s2"],
        "varEntra": "x1",
        "varSale": "s1"
      }
    }
  ]
}
```

`status` ∈ `OPTIMO | INFACTIBLE | NO_ACOTADO | MULTIPLE_OPTIMO | ERROR`

Infactible y no acotado son resultados válidos (HTTP 200), no errores.

---

## Módulo LP — Implementado

### Estructura común de `SolucionLP`

Todos los solvers de LP devuelven `SolveResult<SolucionLP>`.
`SolucionLP` incluye siempre estos campos cuando `status` es `OPTIMO` o `MULTIPLE_OPTIMO`:

```json
{
  "valores": { "x1": 3.0, "x2": 1.5 },
  "holguras": { "s1": 0.0, "s2": 0.0 },
  "valorOptimo": 21.0,
  "preciosSombra": {
    "R1": 0.75,
    "R2": 0.50
  },
  "rangosSensibilidad": {
    "coeficientesObjetivo": [
      { "variable": "x1", "valorActual": 5.0, "min": 4.0,  "max": 8.0 },
      { "variable": "x2", "valorActual": 4.0, "min": -2.0, "max": 4.666667 }
    ],
    "rhs": [
      { "restriccion": "R1", "valorActual": 24.0, "min": 12.0, "max": 36.0 },
      { "restriccion": "R2", "valorActual": 6.0,  "min": 4.0,  "max": 12.0 }
    ]
  }
}
```

| Campo | Descripción |
|---|---|
| `valores` | Valor óptimo de cada variable de decisión |
| `holguras` | Valor de cada variable de holgura/superávit en el óptimo (`sᵢ = 0` → restricción activa) |
| `valorOptimo` | Z* |
| `preciosSombra` | ∂Z\*/∂bᵢ — cuánto mejora Z si se relaja una unidad la restricción Rᵢ |
| `rangosSensibilidad.coeficientesObjetivo` | Rango de cada cᵢ donde la base óptima no cambia (`null` = sin límite ±∞) |
| `rangosSensibilidad.rhs` | Rango de cada bᵢ donde la base óptima sigue siendo factible (`null` = sin límite) |

---

### `POST /api/v1/lp/simplex`

Resuelve PL con el método Simplex estándar.
**Restricción técnica:** solo acepta restricciones `LEQ` (≤) con `rhs >= 0`.
Para `GEQ` o `EQ` → usar `/lp/gran-m` o `/lp/dos-fases`.

**Request:**
```json
{
  "variables": ["x1", "x2"],
  "objetivo": {
    "coeficientes": [5.0, 4.0],
    "tipo": "MAXIMIZAR"
  },
  "restricciones": [
    { "coeficientes": [6.0, 4.0], "tipo": "LEQ", "rhs": 24.0 },
    { "coeficientes": [1.0, 2.0], "tipo": "LEQ", "rhs": 6.0 }
  ]
}
```

`tipo` del objetivo: `MAXIMIZAR` | `MINIMIZAR`
`tipo` de restricción: `LEQ` | `GEQ` | `EQ`

**Response:** `SolveResult<SolucionLP>` (ver estructura arriba)

`steps[i].datos` siempre contiene:
- `encabezados`: lista de nombres de columnas (`variables + holguras + "b"`)
- `tableau`: matriz 2D de doubles (filas de restricciones + fila z)
- `base`: nombres de las variables básicas actuales
- `varEntra` / `varSale` (solo en pasos de iteración)
- `valorOptimo` + `status` (solo en el paso final)

**Errores (HTTP 400):**
```json
{ "error": "Simplex estándar solo admite restricciones <=. Restricción 2 es de tipo GEQ." }
```

---

### `POST /api/v1/lp/gran-m`

Resuelve PL con el método Gran M (penalidad). Acepta `LEQ`, `GEQ` y `EQ`.

**Request:** igual que `/lp/simplex` pero `tipo` de restricción puede ser `GEQ` o `EQ`.

**Response:** `SolveResult<SolucionLP>` con los mismos campos. Los pasos incluyen
las variables artificiales (`a1`, `a2`...) en `encabezados` y `base`.

---

### `POST /api/v1/lp/dos-fases`

Resuelve PL con el método Dos Fases. Acepta `LEQ`, `GEQ` y `EQ`.

**Request:** igual que `/lp/gran-m`.

**Response:** `SolveResult<SolucionLP>`. Los pasos incluyen pasos de Fase 1
(títulos con `"Fase 1"`) y Fase 2 (títulos con `"Fase 2"`), con numeración continua.

---

## Módulo AI — Implementado

### `POST /api/v1/ai/chat`

Entrypoint y orquestador principal de la IA. El tutor guía al estudiante y, según el
contexto, puede invocar hasta tres tools en un mismo turno. Los datos estructurados que
generan las tools se incluyen en `ChatResponse` como campos nullables — el frontend los
lee y actualiza el formulario y/o tableau automáticamente.

**Request — primera llamada (`sesionId: null` genera nueva sesión):**
```json
{
  "sesionId": null,
  "mensaje": "Tengo un problema de producción de mesas y sillas..."
}
```

**Request — turnos siguientes:**
```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "mensaje": "sí, resuélvelo"
}
```

**Response — respuesta conversacional pura (sin tools):**
```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "respuesta": "Bien identificadas. ¿Cuánta ganancia genera cada mesa y cada silla?",
  "modeloSugerido": null,
  "validacion": null,
  "resultado": null
}
```

**Response — cuando el tutor sugiere un modelo (`modeloSugerido` non-null):**
```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "respuesta": "Identifiqué x1=mesas y x2=sillas. La función objetivo es MAX Z = 50x1 + 30x2 ya que esas son las ganancias por unidad. Las restricciones capturan horas de carpintería y metros de madera. Esta es una sugerencia — revisa que los coeficientes sean correctos antes de continuar.",
  "modeloSugerido": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [50.0, 30.0], "tipo": "MAXIMIZAR" },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  },
  "validacion": null,
  "resultado": null
}
```

**Response — cuando el tutor quiere resolver (Human-in-the-Loop, `solicitudAprobacion` non-null):**

El solver NO se ejecuta todavía. La UI debe mostrar el modelo y el método con botones
**Aprobar / Rechazar**, y enviar la decisión a `POST /api/v1/ai/chat/aprobacion`.

```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "respuesta": "Envié la solicitud para resolver con Simplex estándar. Revisa el modelo y confírmalo con el botón Aprobar.",
  "modeloSugerido": null,
  "validacion": { "esValido": true, "analisis": "...", "erroresEncontrados": [], "sugerencias": [], "modeloCorregido": null },
  "resultado": null,
  "resultadoGrafico": null,
  "solicitudAprobacion": {
    "solicitudId": "7c1e...uuid",
    "metodo": "SIMPLEX",
    "modelo": {
      "variables": ["x1", "x2"],
      "objetivo": { "coeficientes": [50.0, 30.0], "tipo": "MAXIMIZAR" },
      "restricciones": [
        { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
        { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
      ]
    }
  }
}
```

`metodo` ∈ `SIMPLEX | GRAN_M | DOS_FASES | GRAFICO`. Una solicitud expira a los 15
minutos sin decisión; una nueva solicitud de la misma sesión reemplaza la anterior.

**Response — con resultado del solver (solo llega vía `/chat/aprobacion` tras aprobar):**
```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "respuesta": "El modelo es correcto. Z* = 21.0 con x1=3 mesas y x2=1.5 sillas. En la iteración 1 entró x1 porque tenía el coeficiente más negativo en la fila z (-5). ¿Qué significa este resultado para tu problema?",
  "modeloSugerido": null,
  "validacion": {
    "esValido": true,
    "analisis": "El modelo refleja correctamente el enunciado.",
    "erroresEncontrados": [],
    "sugerencias": [],
    "modeloCorregido": null
  },
  "resultado": {
    "status": "OPTIMO",
    "solution": {
      "valores": { "x1": 3.0, "x2": 1.5 },
      "holguras": { "s1": 0.0, "s2": 0.0 },
      "valorOptimo": 21.0,
      "preciosSombra": { "R1": 0.75, "R2": 0.5 },
      "rangosSensibilidad": {
        "coeficientesObjetivo": [
          { "variable": "x1", "valorActual": 5.0, "min": 4.0, "max": 8.0 },
          { "variable": "x2", "valorActual": 4.0, "min": -2.0, "max": 4.666667 }
        ],
        "rhs": [
          { "restriccion": "R1", "valorActual": 24.0, "min": 12.0, "max": 36.0 },
          { "restriccion": "R2", "valorActual": 6.0,  "min": 4.0,  "max": 12.0 }
        ]
      }
    },
    "steps": [ ... ]
  }
}
```

**Regla del frontend:** verificar cada campo antes de usar — cualquiera puede ser `null`.
`resultado.solution` también puede ser `null` si `status` es `NO_ACOTADO` o `INFACTIBLE`.

El `sesionId` (un UUID que genera el servidor) se persiste en PostgreSQL junto con la memoria
del LLM: la conversación **sobrevive al reinicio del servidor**. Si el cliente envía un
`sesionId` nulo, en blanco o que no sea un UUID, se abre una sesión nueva.

---

### `GET /api/v1/ai/chat/{sesionId}/historial`

Transcript persistido de la sesión, para rehidratar la UI tras un F5 o un reinicio del backend.
Los mensajes internos `[SISTEMA]` (reanudación tras una decisión HITL) no aparecen como mensajes
del estudiante: de esos turnos solo se devuelve la respuesta del tutor.

**Respuesta 200**
```json
[
  { "rol": "user",  "texto": "Maximizar 5x1 + 4x2", "fecha": "2026-07-09T08:31:02.114" },
  { "rol": "tutor", "texto": "¿Qué representa x1 en tu problema?", "fecha": "2026-07-09T08:31:04.902" }
]
```

**404** — la sesión no existe (o expiró por retención). El cliente debe descartar el `sesionId`
guardado y empezar una sesión nueva.

---

### `GET /api/v1/ai/chat/{sesionId}/actividad`

**Qué está haciendo Pivot ahora mismo.** `POST /ai/chat` es bloqueante y no cuenta nada por el
camino, así que la fase viaja por este canal aparte: la UI lo **sondea cada 400 ms** mientras
dura el turno y va mutando el texto del indicador ("Pivot está escribiendo…" → "Validando tu
modelo…" → "Resolviendo con MODI…").

El `texto` viene **ya compuesto** desde el backend — el cliente no traduce nada, solo lo pinta.
El nombre del algoritmo es el real (MODI, Vogel, EOQ con descuentos), no el del enum
`MetodoResolucion`. Ver `docs/ARQUITECTURA_IA.md` §4.

**Respuesta 200**
```json
{
  "fase": "RESOLVIENDO",
  "texto": "Resolviendo con MODI",
  "secuencia": 42
}
```

`fase` ∈ `PENSANDO`, `ENRUTANDO`, `FORMULANDO`, `VALIDANDO`, `PREPARANDO`, `RESOLVIENDO`, `EXPLICANDO`.

`secuencia` es un contador monótono: dos sondeos en vuelo pueden volver desordenados, así que el
cliente descarta cualquier respuesta con una secuencia menor que la ya pintada.

**204 No Content** — no hay ningún turno en curso en esa sesión. **No es un error.** El cliente
debe tratarlo como "sin novedad" y NO borrar la fase que ya tenía pintada: al arrancar un turno
hay una ventana en la que el POST todavía no llegó al servidor y este endpoint devuelve 204.

> ⚠ El `sesionId` de una conversación nueva lo acuña el **cliente** (`crypto.randomUUID()`) y lo
> envía en el primer `POST /ai/chat` — el backend acepta cualquier UUID entrante. Si lo acuñara el
> servidor, el primer turno no tendría `sesionId` que sondear.

---

### `POST /api/v1/ai/chat/aprobacion`

**Human-in-the-Loop.** Comunica la decisión del estudiante sobre la solicitud de
resolución pendiente (`ChatResponse.solicitudAprobacion`). Es la única vía por la que
un solver se ejecuta desde el chat: la compuerta es estructural, no depende del LLM.

- **Aprobar** → el backend ejecuta el solver, informa al tutor y devuelve un
  `ChatResponse` con `resultado` (o `resultadoGrafico`) y la explicación del tutor.
- **Rechazar** → el solver no corre; el `comentario` re-alimenta al tutor, que retoma
  la conversación (típicamente proponiendo un `modeloSugerido` corregido).

**Request:**
```json
{
  "solicitudId": "7c1e...uuid",
  "aprobado": true,
  "comentario": null,
  "modeloModificado": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [10.0, 10.0], "tipo": "MAXIMIZAR" },
    "restricciones": [...]
  }
}
```

- **`modeloModificado` (opcional)**: JSON con el modelo actualmente editado y visible en el formulario de la interfaz. Si se envía junto con `aprobado: true`, el backend sobrescribe el modelo que la IA tenía en la solicitud y ejecuta el solver sobre este modelo modificado, garantizando concordancia 100% para cualquier módulo (`PL`, `INVENTARIO`, `TRANSPORTE`, `REDES`, `ENTERA`, `DINAMICA`).

En un rechazo, `comentario` es opcional pero recomendado — el tutor lo usa para corregir:
```json
{
  "solicitudId": "7c1e...uuid",
  "aprobado": false,
  "comentario": "la ganancia de las mesas es 60, no 50"
}
```

**Response:** un `ChatResponse` normal (mismo `sesionId` de la conversación). Tras
aprobar, `resultado`/`resultadoGrafico` traen el `SolveResult` completo y `respuesta`
la explicación del tutor. Tras rechazar, suele venir `modeloSugerido` con la corrección.

**Errores:** `400` si `solicitudId` no existe, ya fue decidida o expiró (15 min).

---

### `POST /api/v1/ai/sugerir-modelo`

Extrae un `ModeloLP` estructurado desde lenguaje natural. Devuelve una **sugerencia
modificable** — el usuario debe revisarla antes de usar.

**Request:**
```json
{
  "descripcionProblema": "Maximizar ganancia: mesas $50, sillas $30. Carpintería: 400h (mesa 20h, silla 10h). Madera: 450m (mesa 30m, silla 15m)."
}
```

**Response:**
```json
{
  "modelo": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [50.0, 30.0], "tipo": "MAXIMIZAR" },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  },
  "razonamiento": "x1=mesas, x2=sillas. La ganancia de cada producto forma los coeficientes del objetivo...",
  "supuestosAplicados": ["x1 representa unidades de mesas", "se asume no negatividad implícita"],
  "advertencias": []
}
```

---

### `POST /api/v1/ai/validar-modelo`

Valida el modelo propuesto por el estudiante contra la descripción original del problema.
Detecta coeficientes incorrectos, restricciones faltantes, tipo de optimización erróneo, etc.

**Request:**
```json
{
  "descripcionProblema": "Mesas: $50 ganancia, 20h carpintería, 30m madera. Sillas: $30, 10h, 15m. Límites: 400h y 450m.",
  "modelo": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [30.0, 50.0], "tipo": "MAXIMIZAR" },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  }
}
```

**Response — modelo con errores:**
```json
{
  "esValido": false,
  "analisis": "Los coeficientes de la función objetivo están invertidos...",
  "erroresEncontrados": [
    "El coeficiente de x1 (mesas) debería ser 50, no 30",
    "El coeficiente de x2 (sillas) debería ser 30, no 50"
  ],
  "sugerencias": ["Revisa qué variable representa mesas y qué representa sillas"],
  "modeloCorregido": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [50.0, 30.0], "tipo": "MAXIMIZAR" },
    "restricciones": [ ... ]
  }
}
```

**Response — modelo válido:**
```json
{
  "esValido": true,
  "analisis": "El modelo es correcto. Los coeficientes coinciden con el enunciado.",
  "erroresEncontrados": [],
  "sugerencias": [],
  "modeloCorregido": null
}
```

---

## Transporte (IMPLEMENTADO)

Cuatro endpoints, uno por método. Todos aceptan el mismo body `ModeloTransporte` y
devuelven `SolveResult<SolucionTransporte>`. Los tres primeros dan una solución básica
inicial; `modi` da el óptimo.

```
POST /api/v1/transporte/esquina-noroeste
POST /api/v1/transporte/costo-minimo
POST /api/v1/transporte/vogel
POST /api/v1/transporte/modi
```

**Request** (`ModeloTransporte`) — `costos` es fila por origen, columna por destino:
```json
{
  "origenes": ["O1", "O2", "O3"],
  "destinos": ["D1", "D2", "D3"],
  "oferta":   [20, 30, 25],
  "demanda":  [30, 25, 20],
  "costos": [[4, 6, 8], [6, 4, 2], [2, 8, 6]]
}
```
Si Σoferta ≠ Σdemanda el backend balancea solo (agrega un origen/destino `"Ficticio"` de costo 0).

**Response** — `solution` es `SolucionTransporte`:
```json
{
  "status": "OPTIMO",
  "solution": {
    "origenes": ["O1", "O2", "O3"],
    "destinos": ["D1", "D2", "D3"],
    "asignaciones": [[20,0,0],[0,10,20],[10,15,0]],
    "costoTotal": 240,
    "comparativaInicial": [
      { "metodo": "ESQUINA_NOROESTE", "costoInicial": 380 },
      { "metodo": "COSTO_MINIMO",     "costoInicial": 240 },
      { "metodo": "VOGEL",            "costoInicial": 240 }
    ],
    "metodoInicial": "COSTO_MINIMO"
  },
  "steps": [ /* cada paso: datos.tipo="TRANSPORTE", origenes, destinos, costos, oferta, demanda,
                asignaciones (null=celda no básica); en MODI además u, v, costosReducidos,
                celdaEntrante, celdaSaliente, ciclo, theta */ ]
}
```
`comparativaInicial` y `metodoInicial` solo se rellenan en `modi` (null en los iniciales).

---

## Redes (IMPLEMENTADO)

Cinco endpoints, uno por problema. Todos aceptan el mismo body `ModeloRed` (cada endpoint
fuerza su método) y devuelven `SolveResult<SolucionRed>`.

```
POST /api/v1/redes/dijkstra             → ruta más corta (pesos ≥ 0)
POST /api/v1/redes/kruskal              → árbol de expansión mínima (no dirigido)
POST /api/v1/redes/edmonds-karp         → flujo máximo fuente→sumidero
POST /api/v1/redes/flujo-costo-minimo   → flujo máximo de costo mínimo fuente→sumidero
POST /api/v1/redes/asignacion           → asignación agentes→tareas (vía red MCF)
```

**Request** (`ModeloRed`) — cada arista usa los campos de su método
(`peso` → Dijkstra/Kruskal · `capacidad` → EK/MCF · `costo` → MCF):
```json
{
  "nodos": ["A", "B", "C", "D", "E"],
  "aristas": [
    { "origen": "A", "destino": "B", "peso": 4 },
    { "origen": "A", "destino": "C", "peso": 2 },
    { "origen": "C", "destino": "B", "peso": 1 },
    { "origen": "B", "destino": "D", "peso": 5 },
    { "origen": "D", "destino": "E", "peso": 2 }
  ],
  "dirigido": true,
  "fuente": "A",
  "sumidero": "E"
}
```
- `fuente`/`sumidero`: obligatorios en EK/MCF; en Dijkstra la fuente es obligatoria y el
  sumidero opcional (sin él se devuelven las distancias a todos los nodos); Kruskal no los usa.
- **Asignación** usa otro cuerpo (el backend construye la red bipartita y balancea con un
  agente/tarea `"Ficticio"` de costo 0 si n ≠ m):
```json
{
  "agentes": ["A1", "A2", "A3"],
  "tareas":  ["T1", "T2", "T3"],
  "matrizCostos": [[9, 2, 7], [6, 4, 3], [5, 8, 1]]
}
```

**Response** — `solution` es `SolucionRed` unificada (campos null según método):
```json
{
  "status": "OPTIMO",
  "solution": {
    "distancias": { "A": 0, "B": 3, "C": 2, "D": 8, "E": 10 },
    "rutaOptima": ["A", "C", "B", "D", "E"],
    "aristasSolucion": [ { "origen": "A", "destino": "C", "peso": 2 } ],
    "flujoPorArco": null,
    "asignacion": null,
    "valorObjetivo": 10,
    "flujoTotal": null,
    "costoTotal": null
  },
  "steps": [ /* cada paso: datos.tipo="REDES", metodo, nodos, aristas (con estado:
                normal|activa|solucion|descartada, y flujo en EK/MCF), fuente?, sumidero?;
                extras por método: distancias/nodoActual (Dijkstra), aristaEvaluada/
                pesoAcumulado (Kruskal), camino/cuelloBotella/flujoTotal (EK),
                +costoUnitario/costoAcumulado (MCF), asignacion/costoTotal (Asignación) */ ]
}
```
- `valorObjetivo` = distancia al sumidero / peso del árbol / flujo máximo / costo mínimo /
  costo de la asignación, según el método. EK/MCF/Asignación llenan `flujoPorArco`
  (clave `"origen->destino"`) y `flujoTotal`; MCF/Asignación además `costoTotal`;
  Asignación llena `asignacion` (agente→tarea, sin ficticios).
- No-factibilidad (sumidero inalcanzable, grafo desconexo, sin camino s→t) →
  `status: "INFACTIBLE"` con `solution: null` (no es un error HTTP).

---

## Programación Dinámica (IMPLEMENTADO)

Cinco endpoints, uno por submodelo. Todos aceptan el mismo body `ModeloDinamico` (cada endpoint fuerza
su método; los campos que no apliquen al submodelo se omiten) y devuelven `SolveResult<SolucionDinamica>`.

```
POST /api/v1/dinamica/asignacion-recursos       → reparto de un recurso entre actividades/periodos
POST /api/v1/dinamica/mochila                   → selección de artículos/proyectos/inversiones
POST /api/v1/dinamica/ruta-etapas               → ruta secuencial sobre una red por etapas
POST /api/v1/dinamica/planificacion-produccion  → producción e inventarios por etapas
POST /api/v1/dinamica/reemplazo-equipos         → conservar o reemplazar un equipo cada año
```

**Request** (`ModeloDinamico`) — ejemplo de mochila 0/1 (`unidadesMaximas` omitido ⇒ 1):
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

Ejemplo de ruta por etapas (la etapa 1 lleva un único nodo: el origen; cada arco avanza una etapa):
```json
{
  "etapasRuta": [
    { "etapa": 1, "nodos": ["A"] },
    { "etapa": 2, "nodos": ["B", "C"] },
    { "etapa": 3, "nodos": ["D"] }
  ],
  "arcos": [
    { "origen": "A", "destino": "B", "costo": 2 },
    { "origen": "A", "destino": "C", "costo": 4 },
    { "origen": "B", "destino": "D", "costo": 7 },
    { "origen": "C", "destino": "D", "costo": 3 }
  ]
}
```

**Response** — `solution` es `SolucionDinamica` (`rutaOptima` solo en RUTA_ETAPAS):
```json
{
  "status": "OPTIMO",
  "solution": {
    "valorOptimo": 7.0,
    "tablas": [
      {
        "etapa": 3, "nombreEtapa": "Etapa 3 — C", "recurrencia": "f_3(s) = max{ 5 · x ... }",
        "filas": [
          {
            "estado": "s = 4", "decisionOptima": "x = 1", "valorOptimo": 5.0,
            "evaluaciones": [
              { "decision": "x = 0", "contribucion": 0.0, "valorFuturo": 0.0, "valorTotal": 0.0, "optima": false },
              { "decision": "x = 1", "contribucion": 5.0, "valorFuturo": 0.0, "valorTotal": 5.0, "optima": true }
            ]
          }
        ]
      }
    ],
    "politicaOptima": [
      { "etapa": 1, "nombreEtapa": "A", "estadoEntrada": "s = 5", "decision": "x = 1",
        "contribucion": 3.0, "estadoSalida": "s = 3" }
    ],
    "rutaOptima": null,
    "definicionEtapas": "Etapa i = el artículo i ...",
    "definicionEstados": "Estado s = capacidad que queda libre ...",
    "definicionDecisiones": "Decisión x = unidades del artículo que se cargan ...",
    "funcionRecurrencia": "f_i(s) = max{ v_i · x + f_(i+1)(s - p_i · x) ... }",
    "principioOptimalidad": "Principio de optimalidad de Bellman: ...",
    "interpretacionPolitica": "Carga A y B. Consumes 5 de las 5 unidades ..."
  },
  "steps": [
    { "numero": 1, "titulo": "Formulación del modelo",
      "datos": { "tipo": "PROGRAMACION_DINAMICA", "metodo": "MOCHILA", "etapas": "...", "estados": "...",
                 "decisiones": "...", "recurrencia": "...", "principioOptimalidad": "..." } }
  ]
}
```

Las `tablas` van en el orden de la recursión hacia atrás (`tablas[0]` = última etapa). El paso de
formulación lleva `etapas`/`estados`/`decisiones`/`recurrencia`/`principioOptimalidad`; cada paso de
etapa lleva `datos.tabla`; el paso final lleva `datos.politica`, `datos.valorOptimo` y, si aplica,
`datos.rutaOptima`.

**Casos especiales**
- Entrada malformada (`articulos` vacío, `retornos` de tamaño ≠ `recursoTotal + 1`, `tablaEdades` con
  huecos, arco que salta dos etapas, sentido contrario al del submodelo) → HTTP 400.
- No-factibilidad (`ruta-etapas` sin camino al destino; `planificacion-produccion` con capacidad
  insuficiente) → `status: "INFACTIBLE"` con `solution: null` (no es un error HTTP).
- Ningún valor infinito aparece nunca en la respuesta: los estados inalcanzables se omiten de las tablas.

---

## Módulos pendientes (devuelven 404 por ahora)

```
POST /api/v1/lp/dual                  → pendiente (método dual)
POST /api/v1/transporte/hungaro       → pendiente (asignación por método húngaro;
                                         la asignación ya se resuelve en /api/v1/redes/asignacion)
POST /api/v1/entera/gomory            → pendiente (cortes de Gomory;
                                         PL entera ya se resuelve en /api/v1/entera/branch-and-bound)
```

---

## Errores globales

`GlobalExceptionHandler` en `infrastructure/web/` mapea:

| Excepción | HTTP | Body |
|---|---|---|
| `IllegalArgumentException` | 400 | `{ "error": "mensaje descriptivo" }` |
| Cualquier otra | 500 | `{ "error": "Error interno: ..." }` |

Los errores de validación del dominio usan `IllegalArgumentException`.
Los errores del LLM (timeout, Groq down) se propagan como 500.
