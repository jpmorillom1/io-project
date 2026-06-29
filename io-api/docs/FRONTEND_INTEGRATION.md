# Guía de integración Frontend — io-api

> Documento para el agente/equipo React que va a consumir la API.
> Base URL local: `http://localhost:8080`
> CORS habilitado para cualquier `localhost:*` (Vite corre en 5173 por defecto).

---

## Resumen de endpoints

| Método | URL | Para qué |
|--------|-----|----------|
| POST | `/api/v1/lp/simplex` | Resolver PL — Simplex estándar (solo ≤) |
| POST | `/api/v1/lp/gran-m` | Resolver PL — Gran M (≤, ≥, =) |
| POST | `/api/v1/lp/dos-fases` | Resolver PL — Dos Fases (≤, ≥, =) |
| POST | `/api/v1/ai/chat` | Chat socrático con el tutor Ío |
| POST | `/api/v1/ai/sugerir-modelo` | Extraer un `ModeloLP` desde texto libre |
| POST | `/api/v1/ai/validar-modelo` | Validar el modelo del estudiante |

Todos aceptan y devuelven `application/json`. Todos los errores de validación
devuelven HTTP 400 con `{ "error": "descripción del problema" }`.

---

## 1. Resolver Simplex — sin IA

El endpoint puro del solver. Úsalo cuando el usuario ya tiene el modelo construido
y quiere resolverlo directamente desde el formulario.

### Request

```
POST /api/v1/lp/simplex
Content-Type: application/json
```

```json
{
  "variables": ["x1", "x2"],
  "objetivo": {
    "coeficientes": [5, 4],
    "tipo": "MAXIMIZAR"
  },
  "restricciones": [
    { "coeficientes": [6, 4], "tipo": "LEQ", "rhs": 24 },
    { "coeficientes": [1, 2], "tipo": "LEQ", "rhs": 6 }
  ]
}
```

**Valores válidos:**
- `tipo` del objetivo: `"MAXIMIZAR"` | `"MINIMIZAR"`
- `tipo` de restricción: `"LEQ"` (≤) — único soportado por ahora

### Response exitosa

```json
{
  "status": "OPTIMO",
  "solution": {
    "valores": { "x1": 3.0, "x2": 1.5 },
    "holguras": { "s1": 0.0, "s2": 0.0 },
    "valorOptimo": 21.0,
    "preciosSombra": { "R1": 0.75, "R2": 0.5 },
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
  },
  "steps": [
    {
      "numero": 0,
      "titulo": "Tableau inicial",
      "descripcion": "Se agregan 2 variables de holgura (s1..s2). La base inicial son las holguras con solución básica factible x=0.",
      "datos": {
        "encabezados": ["x1", "x2", "s1", "s2", "b"],
        "tableau": [
          [6.0, 4.0, 1.0, 0.0, 24.0],
          [1.0, 2.0, 0.0, 1.0,  6.0],
          [-5.0,-4.0, 0.0, 0.0,  0.0]
        ],
        "base": ["s1", "s2"]
      }
    },
    {
      "numero": 1,
      "titulo": "Iteración 1: entra x1, sale s1",
      "descripcion": "Pivote en fila 1, columna 'x1'. La variable 's1' sale de la base.",
      "datos": {
        "encabezados": ["x1", "x2", "s1", "s2", "b"],
        "tableau": [
          [1.0, 0.666667, 0.166667, 0.0, 4.0],
          [0.0, 1.333333,-0.166667, 1.0, 2.0],
          [0.0,-0.666667, 0.833333, 0.0,20.0]
        ],
        "base": ["x1", "s2"],
        "varEntra": "x1",
        "varSale": "s1"
      }
    },
    {
      "numero": 3,
      "titulo": "Solución óptima encontrada",
      "descripcion": "No quedan coeficientes negativos en la fila z. Valor óptimo Z* = 21.0.",
      "datos": {
        "encabezados": ["x1", "x2", "s1", "s2", "b"],
        "tableau": [[...]],
        "base": ["x1", "x2"],
        "valorOptimo": 21.0,
        "status": "OPTIMO"
      }
    }
  ]
}
```

### Estructura de `steps[i].datos`

| Campo | Tipo | Presente en |
|-------|------|-------------|
| `encabezados` | `string[]` | todos los pasos |
| `tableau` | `number[][]` | todos los pasos |
| `base` | `string[]` | todos los pasos |
| `varEntra` | `string` | pasos de iteración (1, 2, ...) |
| `varSale` | `string` | pasos de iteración (1, 2, ...) |
| `valorOptimo` | `number` | solo el paso final |
| `status` | `string` | solo el paso final |

El tableau tiene `m+1` filas (m restricciones + fila z) y `n+m+1` columnas
(variables originales + holguras + columna b). La **última fila es siempre la fila z**.

### Valores posibles de `status`

| Valor | Significado |
|-------|-------------|
| `OPTIMO` | Solución encontrada, `solution` tiene los valores |
| `NO_ACOTADO` | La función objetivo crece sin límite, `solution` es `null` |
| `INFACTIBLE` | No hay solución factible, `solution` es `null` |
| `MULTIPLE_OPTIMO` | Hay infinitas soluciones óptimas, `solution` tiene una de ellas |
| `ERROR` | Error interno del solver |

### Errores HTTP 400

```json
{ "error": "Simplex estándar solo admite restricciones <=. Restricción 2 es de tipo GEQ." }
{ "error": "Restricción 1 tiene b=-5. Simplex estándar requiere b >= 0." }
{ "error": "La función objetivo debe tener 2 coeficiente(s)." }
```

---

## 2. Chat socrático — Tutor Ío (orquestador principal)

**Este es el endpoint central de la app.** El tutor no solo responde texto — cuando
toma decisiones (sugerir modelo, validar, resolver) invoca tools internas que escriben
datos estructurados en `ChatResponse`. El frontend debe leer los campos nullables y
actualizar el formulario y el tableau automáticamente sin que el usuario haga nada extra.

### Request

```
POST /api/v1/ai/chat
Content-Type: application/json
```

```json
{
  "sesionId": null,
  "mensaje": "Tengo un problema de producción de mesas y sillas..."
}
```

Usa `sesionId: null` en el primer turno. El backend genera el UUID y lo devuelve.
**Guárdalo** y reenvíalo en cada turno siguiente.

### Response — estructura completa

```json
{
  "sesionId": "a3f9c1d2-7b8e-4f1a-9c2d-0e5f6a7b8c9d",
  "respuesta": "Texto conversacional del tutor — siempre presente",
  "modeloSugerido": null,
  "validacion": null,
  "resultado": null
}
```

Los tres últimos campos son **nullable**. Verifica siempre antes de usar:

| Campo | Non-null significa | Acción del frontend |
|---|---|---|
| `modeloSugerido` | El tutor formuló el modelo LP | Pre-llenar el formulario |
| `validacion` | El tutor evaluó el modelo del estudiante | Mostrar errores inline (o confirmar válido) |
| `resultado` | El tutor resolvió el problema | Mostrar tableau con navegación de pasos |

### Ejemplo — tutor sugiere modelo

```json
{
  "sesionId": "a3f9c1d2-...",
  "respuesta": "Identifiqué x1=mesas y x2=sillas. MAX Z = 50x1 + 30x2 ya que esas son las ganancias. Las restricciones capturan carpintería (400h) y madera (450m). Esta es una sugerencia — revisa que los coeficientes sean correctos.",
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

### Ejemplo — tutor valida y resuelve en el mismo turno

```json
{
  "sesionId": "a3f9c1d2-...",
  "respuesta": "El modelo es correcto. Z* = 21 con x1=3 mesas y x2=1.5 sillas. En la iteración 1 entró x1 porque tenía el coeficiente más negativo en la fila z. ¿Qué significa esto para tu problema?",
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
          { "variable": "x1", "valorActual": 5.0, "min": 4.0,  "max": 8.0 },
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

### Notas importantes

- `sesionId` → UUID generado en el primer turno, persiste en `sessionStorage`
- Memoria en **RAM** — se pierde al reiniciar el servidor
- Máximo 30 mensajes por sesión (ventana deslizante)
- `resultado.solution` puede ser `null` si `status` es `NO_ACOTADO` o `INFACTIBLE`
- El tutor puede encadenar tools: validar con errores + sugerir versión corregida en un turno

---

## 3. Sugerir modelo — IA extrae desde texto

Llama a esto cuando el usuario ingresa la descripción de su problema en texto libre
y quieres pre-llenar el formulario con un modelo sugerido.

### Request

```
POST /api/v1/ai/sugerir-modelo
Content-Type: application/json
```

```json
{
  "descripcionProblema": "Maximizar ganancia: mesas $50, sillas $30. Carpintería: 400h disponibles (mesa 20h, silla 10h). Madera: 450m disponibles (mesa 30m, silla 15m)."
}
```

### Response

```json
{
  "modelo": {
    "variables": ["x1", "x2"],
    "objetivo": {
      "coeficientes": [50.0, 30.0],
      "tipo": "MAXIMIZAR"
    },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  },
  "razonamiento": "x1 representa mesas y x2 sillas. La ganancia de cada producto da los coeficientes del objetivo. Las horas de carpintería y metros de madera son las restricciones.",
  "supuestosAplicados": [
    "x1 = unidades de mesas",
    "x2 = unidades de sillas",
    "Se asume no negatividad implícita"
  ],
  "advertencias": []
}
```

**Este modelo es una SUGERENCIA** — muéstralo pre-llenado en el formulario pero
permitiendo que el usuario lo modifique antes de resolver.

Si la IA no puede extraer un modelo claro:
```json
{
  "modelo": null,
  "razonamiento": "El texto no describe suficientes datos para formular un modelo de PL.",
  "supuestosAplicados": [],
  "advertencias": ["Falta la función objetivo", "No se identificaron restricciones numéricas"]
}
```

---

## 4. Validar modelo — IA revisa el modelo del estudiante

Llama a esto cuando el usuario terminó de completar el formulario y quiere
verificar si su modelo es correcto antes de resolver.

### Request

```
POST /api/v1/ai/validar-modelo
Content-Type: application/json
```

```json
{
  "descripcionProblema": "Mesas: $50 ganancia, 20h carpintería, 30m madera. Sillas: $30, 10h, 15m. Límites: 400h y 450m. Maximizar ganancia.",
  "modelo": {
    "variables": ["x1", "x2"],
    "objetivo": {
      "coeficientes": [30.0, 50.0],
      "tipo": "MAXIMIZAR"
    },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  }
}
```

### Response — modelo con errores

```json
{
  "esValido": false,
  "analisis": "Los coeficientes de la función objetivo están invertidos. El enunciado indica que mesas ($50) es x1 y sillas ($30) es x2, pero el modelo tiene los valores al revés.",
  "erroresEncontrados": [
    "Coeficiente de x1 debería ser 50 (mesas), no 30",
    "Coeficiente de x2 debería ser 30 (sillas), no 50"
  ],
  "sugerencias": [
    "Verifica qué variable definiste para mesas y qué para sillas"
  ],
  "modeloCorregido": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [50.0, 30.0], "tipo": "MAXIMIZAR" },
    "restricciones": [
      { "coeficientes": [20.0, 10.0], "tipo": "LEQ", "rhs": 400.0 },
      { "coeficientes": [30.0, 15.0], "tipo": "LEQ", "rhs": 450.0 }
    ]
  }
}
```

### Response — modelo correcto

```json
{
  "esValido": true,
  "analisis": "El modelo es correcto. Las variables, función objetivo y restricciones coinciden con el enunciado.",
  "erroresEncontrados": [],
  "sugerencias": [],
  "modeloCorregido": null
}
```

---

## Flujo completo recomendado para la UI

```
┌─────────────────────────────────────────────────────────┐
│  Opción A — Flujo asistido por IA (recomendado)         │
└─────────────────────────────────────────────────────────┘

1. Usuario escribe el enunciado del problema en un textarea
       │
       ▼
2. POST /api/v1/ai/sugerir-modelo
   → Pre-llena el formulario con el modelo sugerido
       │
       ▼
3. Usuario revisa y edita el formulario
       │
       ▼
4. POST /api/v1/ai/validar-modelo
   → Si esValido=false: muestra errores, ofrece modeloCorregido
   → Si esValido=true: habilita botón "Resolver"
       │
       ▼
5. POST /api/v1/lp/simplex
   → Muestra solución y pasos del tableau


┌─────────────────────────────────────────────────────────┐
│  Opción B — Flujo directo (sin IA)                      │
└─────────────────────────────────────────────────────────┘

1. Usuario llena el formulario manualmente
       │
       ▼
2. POST /api/v1/lp/simplex directamente
   → Muestra solución y pasos


┌─────────────────────────────────────────────────────────┐
│  Opción C — Chat socrático (tutor Ío)                   │
└─────────────────────────────────────────────────────────┘

1. Turno 1: sesionId=null, el backend genera el UUID
2. Turnos N: mismo sesionId
3. El tutor guía → valida → resuelve internamente cuando
   el modelo está listo y el estudiante lo pide
4. Opcional: al final, hacer POST /api/v1/lp/simplex con
   el modelo validado para mostrar el tableau paso a paso
```

---

## TypeScript — tipos sugeridos

```typescript
// Tipos del dominio
type TipoObjetivo = 'MAXIMIZAR' | 'MINIMIZAR'
type TipoRestriccion = 'LEQ' | 'GEQ' | 'EQ'
type SolveStatus = 'OPTIMO' | 'INFACTIBLE' | 'NO_ACOTADO' | 'MULTIPLE_OPTIMO' | 'ERROR'

interface Restriccion {
  coeficientes: number[]
  tipo: TipoRestriccion
  rhs: number
}

interface FuncionObjetivo {
  coeficientes: number[]
  tipo: TipoObjetivo
}

interface ModeloLP {
  variables: string[]
  objetivo: FuncionObjetivo
  restricciones: Restriccion[]
}

// Respuesta del solver
interface RangoCoeficiente {
  variable: string
  valorActual: number
  min: number | null   // null = -∞
  max: number | null   // null = +∞
}

interface RangoRHS {
  restriccion: string  // "R1", "R2", ...
  valorActual: number
  min: number | null   // null = -∞
  max: number | null   // null = +∞
}

interface RangosSensibilidad {
  coeficientesObjetivo: RangoCoeficiente[]
  rhs: RangoRHS[]
}

interface SolucionLP {
  valores: Record<string, number>       // x1, x2, ...
  holguras: Record<string, number>      // s1, s2, ... (0 = restricción activa)
  valorOptimo: number
  preciosSombra: Record<string, number> // R1, R2, ... (∂Z*/∂bᵢ)
  rangosSensibilidad: RangosSensibilidad
}

interface StepDatos {
  encabezados: string[]
  tableau: number[][]
  base: string[]
  varEntra?: string
  varSale?: string
  valorOptimo?: number
  status?: SolveStatus
}

interface SolveStep {
  numero: number
  titulo: string
  descripcion: string
  datos: StepDatos
}

interface SolveResult {
  status: SolveStatus
  solution: SolucionLP | null
  steps: SolveStep[]
}

// Endpoints de IA
interface ChatRequest {
  sesionId: string | null
  mensaje: string
}

// Los tres campos inferiores son nullable — verificar siempre antes de usar
interface ChatResponse {
  sesionId: string
  respuesta: string
  modeloSugerido: ModeloLP | null        // non-null → pre-llenar formulario
  validacion: ValidacionResponse | null  // non-null → mostrar errores inline
  resultado: SolveResult | null          // non-null → mostrar tableau
}

interface ModeloSugeridoResponse {
  modelo: ModeloLP | null
  razonamiento: string
  supuestosAplicados: string[]
  advertencias: string[]
}

interface ValidarModeloRequest {
  descripcionProblema: string
  modelo: ModeloLP
}

interface ValidacionResponse {
  esValido: boolean
  analisis: string
  erroresEncontrados: string[]
  sugerencias: string[]
  modeloCorregido: ModeloLP | null
}

// Error genérico
interface ApiError {
  error: string
}
```

---

## Cliente HTTP sugerido (fetch)

```typescript
const API_BASE = 'http://localhost:8080/api/v1'

// Resolver Simplex
async function resolverSimplex(modelo: ModeloLP): Promise<SolveResult> {
  const res = await fetch(`${API_BASE}/lp/simplex`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  if (!res.ok) {
    const err: ApiError = await res.json()
    throw new Error(err.error)
  }
  return res.json()
}

// Chat socrático
async function enviarMensaje(
  sesionId: string | null,
  mensaje: string
): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE}/ai/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sesionId, mensaje }),
  })
  if (!res.ok) {
    const err: ApiError = await res.json()
    throw new Error(err.error)
  }
  return res.json()
}

// Sugerir modelo desde texto
async function sugerirModelo(descripcion: string): Promise<ModeloSugeridoResponse> {
  const res = await fetch(`${API_BASE}/ai/sugerir-modelo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ descripcionProblema: descripcion }),
  })
  if (!res.ok) {
    const err: ApiError = await res.json()
    throw new Error(err.error)
  }
  return res.json()
}

// Validar modelo
async function validarModelo(
  descripcion: string,
  modelo: ModeloLP
): Promise<ValidacionResponse> {
  const res = await fetch(`${API_BASE}/ai/validar-modelo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ descripcionProblema: descripcion, modelo }),
  })
  if (!res.ok) {
    const err: ApiError = await res.json()
    throw new Error(err.error)
  }
  return res.json()
}
```

---

## Cómo renderizar el tableau paso a paso

Cada `step` del resultado del solver tiene un tableau 2D. La última fila es siempre
la **fila z** (función objetivo). El número de columnas = `variables.length + restricciones.length + 1`.

```typescript
// Ejemplo de render del tableau de un paso
function renderTableau(step: SolveStep) {
  const { encabezados, tableau, base, varEntra, varSale } = step.datos
  const m = tableau.length - 1  // número de restricciones (sin la fila z)

  // Columna de base: base[i] es la variable básica de la fila i
  // La última fila (índice m) es la fila z — no tiene variable de base

  // Para resaltar el pivote:
  //   columna del pivote = encabezados.indexOf(varEntra)
  //   fila del pivote = base.indexOf(varSale)   (antes del pivot)
}
```

**Estructura visual del tableau:**

```
         x1      x2      s1      s2      b
base[0]  6.0     4.0     1.0     0.0     24.0   ← restricción 1
base[1]  1.0     2.0     0.0     1.0      6.0   ← restricción 2
  z     -5.0    -4.0     0.0     0.0      0.0   ← fila z (siempre la última)
```

---

## Consideraciones para la UX

1. **Los endpoints de IA pueden tardar 2-8 segundos** (depende de Groq). Muestra un spinner.

2. **El endpoint `/lp/simplex` es instantáneo** (Java puro, sin LLM).

3. **El `sesionId` debe persistir en `sessionStorage`** (no `localStorage`) — se pierde
   cuando el usuario cierra la pestaña, que es el comportamiento esperado.

4. **Si el servidor se reinicia**, el `sesionId` guardado ya no servirá — captura el
   error y genera uno nuevo con `sesionId: null`.

5. **`modeloCorregido` puede ser `null`** en `ValidacionResponse` cuando el modelo es válido.
   Siempre verifica antes de usarlo.

6. **`solution` puede ser `null`** en `SolveResult` cuando `status` es `NO_ACOTADO` o
   `INFACTIBLE`. Siempre verifica antes de acceder a `solution.valores`.
