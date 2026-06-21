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

### `POST /api/v1/lp/simplex`

Resuelve Programación Lineal con el método Simplex estándar.
**Restricción técnica:** solo acepta restricciones `LEQ` (≤) con `rhs >= 0`.
Para `GEQ` o `EQ` → pendiente Dos Fases / Gran M.

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
`tipo` de restricción: `LEQ` (≤) — únicamente soportado actualmente

**Response:** `SolveResult<SolucionLP>`
```json
{
  "status": "OPTIMO",
  "solution": {
    "valores": { "x1": 3.0, "x2": 1.5 },
    "valorOptimo": 21.0
  },
  "steps": [ ... ]
}
```

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

**Response — cuando valida y resuelve en el mismo turno:**
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
    "solution": { "valores": { "x1": 3.0, "x2": 1.5 }, "valorOptimo": 21.0 },
    "steps": [ ... ]
  }
}
```

**Regla del frontend:** verificar cada campo antes de usar — cualquiera puede ser `null`.
`resultado.solution` también puede ser `null` si `status` es `NO_ACOTADO` o `INFACTIBLE`.

El `sesionId` se mantiene en RAM. Se pierde al reiniciar el servidor.

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

## Módulos pendientes (devuelven 404 por ahora)

```
POST /api/v1/lp/dos-fases     → pendiente (restricciones >= y =)
POST /api/v1/lp/gran-m        → pendiente
POST /api/v1/lp/dual          → pendiente
POST /api/v1/lp/sensibilidad  → pendiente
POST /api/v1/lp/grafico       → pendiente (solo 2 variables)

POST /api/v1/transporte/resolver   → pendiente
POST /api/v1/redes/resolver        → pendiente

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
