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

El `sesionId` se mantiene en RAM. Se pierde al reiniciar el servidor.

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
  "comentario": null
}
```

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

## Módulos pendientes (devuelven 404 por ahora)

```
POST /api/v1/lp/dual          → pendiente

POST /api/v1/redes/resolver        → pendiente (ver docs/GUIA_REDES.md)

POST /api/v1/entera/resolver       → pendiente
POST /api/v1/dinamica/resolver     → pendiente
POST /api/v1/inventarios/resolver  → pendiente
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
