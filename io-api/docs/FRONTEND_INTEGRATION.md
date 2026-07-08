# Guía de integración Frontend — io-api

> Documento para el agente/equipo React que va a consumir la API.
> Base URL local: `http://localhost:8080`
> CORS habilitado para cualquier `localhost:*` (Vite corre en 5173 por defecto).

---

## Resumen de endpoints

> **Estado del frontend (io-ui):** LP y **Transporte** están implementados de punta a punta.
> El chat es **adaptativo**: vive en `context/ChatProvider` (montado en `AppShell`, sobrevive a
> la navegación) y `moduloDeRespuesta()` cambia solo entre `/lp` y `/transporte` según lo que el
> tutor detecte. La visualización de grafos usa `components/shared/NetworkGraph.tsx` (SVG genérico,
> reutilizable). Para el próximo módulo (Redes) sigue `docs/GUIA_REDES.md` — reusa NetworkGraph y
> amplía `moduloDeRespuesta()`.
>
> **Backend disponible sin UI aún:** **Redes**, **PL Entera (Branch & Bound)** e **Inventarios
> deterministas** ya están completos en el backend (REST + tool del chat + HITL), pero su frontend
> está pendiente. La sección 5 cubre el contrato de **PL Entera** (visualización natural: **árbol de
> nodos**); la sección 6 cubre **Inventarios** (visualización natural: **desarrollo paso a paso** de
> fórmulas + tarjetas de resultado por submodelo, sin tableau ni grafo).

| Método | URL | Para qué |
|--------|-----|----------|
| POST | `/api/v1/lp/simplex` | Resolver PL — Simplex estándar (solo ≤) |
| POST | `/api/v1/lp/gran-m` | Resolver PL — Gran M (≤, ≥, =) |
| POST | `/api/v1/lp/dos-fases` | Resolver PL — Dos Fases (≤, ≥, =) |
| POST | `/api/v1/lp/grafico` | Resolver PL — Método gráfico (2 variables) |
| POST | `/api/v1/transporte/{esquina-noroeste,costo-minimo,vogel,modi}` | Resolver Transporte (ver `API_CONTRACT.md`) |
| POST | `/api/v1/entera/branch-and-bound` | Resolver PL Entera — Branch & Bound (variables enteras/binarias) |
| POST | `/api/v1/inventario/{eoq-basico,eoq-descuentos,eoq-faltantes,produccion-economica,punto-reorden}` | Resolver Inventarios deterministas (ver sección 6) |
| POST | `/api/v1/ai/chat` | Chat socrático con el tutor Pivot |
| POST | `/api/v1/ai/chat/aprobacion` | HITL: aprobar/rechazar la resolución pendiente |
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
| `resultado` | Se resolvió un LP tabular (Simplex/Gran M/Dos Fases) | Mostrar tableau con navegación de pasos |
| `resultadoGrafico` | Se resolvió por método gráfico (2 variables) | Mostrar la región factible y los vértices |
| `resultadoTransporte` | Se resolvió un problema de transporte | Mostrar la tabla de transporte |
| `resultadoRed` | Se resolvió un problema de redes | Mostrar el grafo con la solución |
| `resultadoEntero` | Se resolvió PL Entera (Branch & Bound) | Mostrar el árbol de nodos (ver sección 5) |
| `resultadoInventario` | Se resolvió un modelo de inventario | Mostrar el desarrollo paso a paso + resultado (ver sección 6) |
| `solicitudAprobacion` | El tutor quiere resolver y espera aprobación (HITL) | Mostrar tarjeta Aprobar/Rechazar |

> Como máximo **uno** de los `resultado*` viene non-null en un mismo turno; el resto de este
> ejemplo muestra solo `resultado` por brevedad, pero la estructura es análoga para los demás.

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

## 5. Resolver PL Entera — Branch & Bound (sin IA)

Endpoint puro del solver de **Programación Lineal Entera**. Úsalo cuando el modelo ya está
construido y una o más variables deben ser **enteras** (cantidades indivisibles) o **binarias**
(decisiones sí/no: seleccionar proyecto, abrir/cerrar sucursal, comprar/no comprar, turnos, rutas).

El modelo reutiliza el `ModeloLP` como **relajación** (variables, objetivo, restricciones) y añade
`tiposVariable`, una lista **alineada por índice** con `variables`. Acepta `LEQ`, `GEQ` y `EQ`
(con `rhs ≥ 0`) — internamente cada relajación se resuelve con Gran M.

### Request

```
POST /api/v1/entera/branch-and-bound
Content-Type: application/json
```

```json
{
  "relajacion": {
    "variables": ["x1", "x2"],
    "objetivo": { "coeficientes": [5, 4], "tipo": "MAXIMIZAR" },
    "restricciones": [
      { "coeficientes": [6, 4], "tipo": "LEQ", "rhs": 24 },
      { "coeficientes": [1, 2], "tipo": "LEQ", "rhs": 6 }
    ]
  },
  "tiposVariable": ["ENTERA", "ENTERA"]
}
```

**Valores válidos de `tiposVariable[i]`:** `"ENTERA"` | `"BINARIA"` | `"CONTINUA"`.
- `BINARIA` fuerza `x ∈ {0,1}` (el solver añade `x ≤ 1` automáticamente; no lo pongas tú).
- `CONTINUA` deja la variable relajada (modelo mixto MILP).

### Response exitosa

```json
{
  "status": "OPTIMO",
  "solution": {
    "valores": { "x1": 4.0, "x2": 0.0 },
    "valorOptimo": 20.0,
    "valorRelajacion": 21.0,
    "nodosExplorados": 5
  },
  "steps": [
    {
      "numero": 0,
      "titulo": "Nodo 0 — Relajación LP inicial (raíz)",
      "descripcion": "La variable 'x2' = 1.5 es fraccionaria. Se ramifica en 'x2 <= 1' y 'x2 >= 2'.",
      "datos": {
        "nodoId": 0,
        "padreId": -1,
        "rama": "Relajación LP inicial (raíz)",
        "estadoRelajacion": "OPTIMO",
        "zRelajacion": 21.0,
        "valoresRelajacion": { "x1": 3.0, "x2": 1.5 },
        "accion": "RAMIFICA",
        "varRamificada": "x2",
        "valorFraccionario": 1.5,
        "ramaIzquierda": "x2 <= 1",
        "ramaDerecha": "x2 >= 2"
      }
    },
    {
      "numero": 2,
      "titulo": "Nodo 4 — x1 <= 3",
      "descripcion": "Solución ENTERA factible con Z = 19.0. Es el nuevo mejor incumbente.",
      "datos": {
        "nodoId": 4,
        "padreId": 2,
        "rama": "x1 <= 3",
        "estadoRelajacion": "OPTIMO",
        "zRelajacion": 19.0,
        "valoresRelajacion": { "x1": 3.0, "x2": 1.0 },
        "accion": "INCUMBENTE"
      }
    },
    {
      "numero": 4,
      "titulo": "Nodo 1 — x2 >= 2",
      "descripcion": "z relajado = 18.0 no mejora el incumbente Z* = 20.0: la rama se poda por cota.",
      "datos": {
        "nodoId": 1,
        "padreId": 0,
        "rama": "x2 >= 2",
        "estadoRelajacion": "OPTIMO",
        "zRelajacion": 18.0,
        "valoresRelajacion": { "x1": 2.0, "x2": 2.0 },
        "accion": "PODA_COTA"
      }
    },
    {
      "numero": 5,
      "titulo": "Solución óptima entera encontrada",
      "descripcion": "Z* = 20.0 con la asignación entera óptima. La relajación LP de la raíz daba 21.0 (brecha de integralidad = 1.0): por eso no basta con redondear la relajación.",
      "datos": {
        "valores": { "x1": 4.0, "x2": 0.0 },
        "valorOptimo": 20.0,
        "valorRelajacion": 21.0,
        "brechaIntegralidad": 1.0,
        "nodosExplorados": 5,
        "status": "OPTIMO"
      }
    }
  ]
}
```

> El ejemplo está recortado: la lista `steps` real incluye **un paso por nodo explorado** en el
> orden en que Branch & Bound los visita (DFS), más el paso final.

### Estructura de `steps[i].datos` (nodos del árbol)

| Campo | Tipo | Presente en |
|-------|------|-------------|
| `nodoId` | `number` | pasos de nodo |
| `padreId` | `number` | pasos de nodo (`-1` en la raíz) |
| `rama` | `string` | pasos de nodo (la restricción que define el nodo, ej. `"x1 <= 3"`) |
| `estadoRelajacion` | `string` | pasos de nodo (`OPTIMO`/`INFACTIBLE`/… o `SIN_SOLUCION`) |
| `zRelajacion` | `number` | pasos de nodo con relajación resuelta |
| `valoresRelajacion` | `Record<string,number>` | pasos de nodo con relajación resuelta |
| `accion` | `string` | pasos de nodo — ver tabla abajo |
| `varRamificada`, `valorFraccionario`, `ramaIzquierda`, `ramaDerecha` | `string`/`number` | solo cuando `accion="RAMIFICA"` |
| `valores`, `valorOptimo`, `valorRelajacion`, `brechaIntegralidad`, `nodosExplorados` | — | solo el paso final |

**Valores de `accion`:**

| `accion` | Significado | Cómo pintarlo |
|----------|-------------|---------------|
| `RAMIFICA` | La relajación es fraccionaria → se abren dos hijos | nodo con dos ramas hacia abajo |
| `INCUMBENTE` | Solución entera factible que mejora el mejor valor | nodo resaltado (candidato/mejor) |
| `PODA_COTA` | La relajación no puede superar el incumbente → se poda | nodo/rama atenuado o tachado |
| `PODA_INFACTIBLE` | La relajación del nodo es infactible → se poda | nodo/rama atenuado o tachado |

### `status` y campos de `solution`

`status` usa la misma enumeración que el resto (`OPTIMO`/`INFACTIBLE`/`NO_ACOTADO`/…).
`solution` es `null` cuando `status` es `INFACTIBLE` (no hay solución entera factible) o `NO_ACOTADO`.

- `valores` — solución entera óptima por variable.
- `valorOptimo` — `Z*` entero.
- `valorRelajacion` — óptimo de la relajación LP en la raíz. La diferencia `|valorRelajacion − valorOptimo|`
  es la **brecha de integralidad**: úsala para explicar por qué **redondear la relajación no es válido**.
- `nodosExplorados` — tamaño del árbol recorrido.

### Errores HTTP 400

```json
{ "error": "tiposVariable debe tener un tipo por cada variable (2)." }
{ "error": "El modelo necesita al menos una variable." }
```

---

## 6. Resolver Inventarios — modelos deterministas (sin IA)

Endpoints puros del módulo **Inventarios**. Cinco submodelos deterministas con demanda conocida.
Cada uno tiene su propio endpoint; **el cuerpo es siempre el mismo record `ModeloInventario`** (con
los campos que aplican al submodelo — el resto se omiten). El endpoint fuerza su método, así que
`metodo` en el body es opcional.

> **Diferencia clave con los demás módulos:** son **fórmulas cerradas**, no algoritmos iterativos.
> Por eso `status` es **siempre `OPTIMO`** con datos válidos (no hay `INFACTIBLE`/`NO_ACOTADO`), y una
> entrada malformada (D≤0, H≤0, P≤D en POQ, tramos vacíos en descuentos…) responde **HTTP 400**.
> Los `steps` **no son iteraciones** sino los **pasos del cálculo** (parámetros → fórmula →
> sustitución → resultado). No hay tableau ni grafo: la visualización natural es una lista de pasos
> más una tarjeta de resultado con los campos del submodelo.

### 6.1 Submodelos, endpoints y parámetros

| Submodelo | Endpoint | Parámetros del body (además de `demanda`, `costoOrden`) |
|-----------|----------|----------------------------------------------------------|
| EOQ básico | `/api/v1/inventario/eoq-basico` | `costoMantener` |
| Producción económica (POQ/EPQ) | `/api/v1/inventario/produccion-economica` | `costoMantener`, `tasaProduccion` (P > demanda) |
| EOQ con faltantes | `/api/v1/inventario/eoq-faltantes` | `costoMantener`, `costoFaltante` |
| Punto de reorden | `/api/v1/inventario/punto-reorden` | `costoMantener`, `leadTimeDias`, `diasHabiles` (opcional, def. 360) |
| EOQ con descuentos | `/api/v1/inventario/eoq-descuentos` | `tramos` (tabla precio) + `tasaMantenerPorcentaje` **o** `costoMantener` |

Campos del `ModeloInventario` (todos los no usados por el submodelo se omiten):

| Campo | Tipo | Para qué |
|-------|------|----------|
| `demanda` | `number` | D — demanda conocida por periodo (anual) — **todos** |
| `costoOrden` | `number` | K — costo de ordenar/preparar un pedido — **todos** |
| `costoMantener` | `number` | H — costo de mantener por unidad-periodo — todos menos descuentos-con-tasa |
| `costoFaltante` | `number` | b — costo de faltante por unidad-periodo — solo faltantes |
| `tasaProduccion` | `number` | P — tasa de producción (P > D) — solo producción económica |
| `leadTimeDias` | `number` | L — tiempo de entrega en días — solo punto de reorden |
| `diasHabiles` | `number` | días hábiles al año (def. 360) — solo punto de reorden |
| `tasaMantenerPorcentaje` | `number` | i — H como fracción del precio (ej. 0.2) — solo descuentos |
| `tramos` | `TramoDescuento[]` | tabla de precios por cantidad — solo descuentos |

`TramoDescuento`: `{ "cantidadMinima": number, "precioUnitario": number }`.

### 6.2 Requests de ejemplo

```jsonc
// EOQ básico
POST /api/v1/inventario/eoq-basico
{ "demanda": 1000, "costoOrden": 50, "costoMantener": 4 }

// Producción económica (POQ/EPQ)
POST /api/v1/inventario/produccion-economica
{ "demanda": 1000, "costoOrden": 50, "costoMantener": 4, "tasaProduccion": 2000 }

// EOQ con faltantes
POST /api/v1/inventario/eoq-faltantes
{ "demanda": 1000, "costoOrden": 50, "costoMantener": 4, "costoFaltante": 10 }

// Punto de reorden
POST /api/v1/inventario/punto-reorden
{ "demanda": 1200, "costoOrden": 40, "costoMantener": 3, "leadTimeDias": 10, "diasHabiles": 360 }

// EOQ con descuentos por cantidad (H = 20% del precio)
POST /api/v1/inventario/eoq-descuentos
{
  "demanda": 5000, "costoOrden": 49, "tasaMantenerPorcentaje": 0.2,
  "tramos": [
    { "cantidadMinima": 0,    "precioUnitario": 5.00 },
    { "cantidadMinima": 1000, "precioUnitario": 4.80 },
    { "cantidadMinima": 2500, "precioUnitario": 4.75 }
  ]
}
```

### 6.3 Response exitosa

`SolucionInventario` es un record **unificado**: solo los campos que aplican al submodelo vienen
poblados, el resto llegan `null`. Ejemplo del EOQ básico:

```json
{
  "status": "OPTIMO",
  "solution": {
    "cantidadOptima": 158.113883,
    "costoTotalAnual": 632.455532,
    "costoOrdenarAnual": 316.227766,
    "costoMantenerAnual": 316.227766,
    "numeroPedidos": 6.324555,
    "tiempoCicloDias": 56.921,
    "nivelMaximoInventario": null,
    "faltanteMaximo": null,
    "costoFaltanteAnual": null,
    "puntoReorden": null,
    "demandaDiaria": null,
    "costoCompraAnual": null,
    "precioUnitarioOptimo": null,
    "comparativa": null,
    "interpretacionPolitica": "Pide 158.11 unidades cada vez que el inventario se agote: realizarás ≈6.32 pedidos al año, uno cada ≈56.92 días hábiles, con un costo total de operación de 632.46 al año (sin contar la compra)."
  },
  "steps": [
    {
      "numero": 1,
      "titulo": "Parámetros del modelo",
      "descripcion": "Demanda anual D = 1000, costo de ordenar K = 50, costo de mantener H = 4 por unidad-año.",
      "datos": { "tipo": "INVENTARIO", "metodo": "EOQ_BASICO" }
    },
    {
      "numero": 2,
      "titulo": "Cantidad económica de pedido (Q*)",
      "descripcion": "Q* = √(2·D·K / H) = √(2·1000·50 / 4) = 158.11 unidades.",
      "datos": {
        "tipo": "INVENTARIO", "metodo": "EOQ_BASICO",
        "formula": "Q* = raíz(2·D·K / H)",
        "sustitucion": "raíz(2·1000·50 / 4)",
        "resultado": 158.113883
      }
    }
    // ... un paso por cada etapa del cálculo (N y ciclo, costo total, etc.)
  ]
}
```

### 6.4 Qué campos de `solution` usar por submodelo

Todos traen: `cantidadOptima` (Q\*), `costoTotalAnual`, `costoOrdenarAnual`, `costoMantenerAnual`,
`numeroPedidos`, `tiempoCicloDias`, `interpretacionPolitica`. Además:

| Submodelo | Campos extra non-null | Notas |
|-----------|------------------------|-------|
| EOQ básico | — | en el óptimo `costoOrdenarAnual == costoMantenerAnual` |
| Producción económica | `nivelMaximoInventario` (Imax) | Imax < Q\* por la reposición gradual |
| EOQ con faltantes | `nivelMaximoInventario` (S), `faltanteMaximo`, `costoFaltanteAnual` | `S + faltanteMaximo == cantidadOptima` |
| Punto de reorden | `puntoReorden` (R), `demandaDiaria` (d) | R = d·L; avisa cuándo pedir |
| EOQ con descuentos | `precioUnitarioOptimo`, `costoCompraAnual`, `comparativa` | `costoTotalAnual` **incluye** la compra (D·C) |

`comparativa` (solo descuentos) es un arreglo de `{ precioUnitario, cantidad, costoTotal, factible }`,
una fila por tramo evaluado. Úsalo para una tabla "precio → cantidad → costo total", resaltando el
tramo ganador (el de menor `costoTotal` con `factible: true`, que coincide con `precioUnitarioOptimo`).

### 6.5 Estructura de `steps[i].datos`

| Campo | Tipo | Presente en |
|-------|------|-------------|
| `tipo` | `"INVENTARIO"` | todos los pasos |
| `metodo` | `string` (submodelo) | todos los pasos |
| `formula` | `string` | pasos de cálculo (fórmula simbólica) |
| `sustitucion` | `string` | pasos de cálculo con números sustituidos |
| `resultado` | `number` | pasos de cálculo con un valor computado |

Los tres últimos son opcionales: un paso puede ser solo texto explicativo (todo el detalle va en
`descripcion`). Para la UI basta con listar `numero`, `titulo`, `descripcion` y, si están, mostrar
`formula`/`sustitucion`/`resultado` como una línea destacada tipo "fórmula → valor".

### 6.6 Errores HTTP 400

```json
{ "error": "Debes indicar la demanda (D) con un valor positivo." }
{ "error": "La tasa de producción P debe ser mayor que la demanda D (P > D); si P ≤ D la producción no alcanza a cubrir la demanda." }
{ "error": "El modelo con descuentos necesita al menos un tramo de precio (tramos)." }
{ "error": "Para EOQ con descuentos indica el costo de mantener fijo (costoMantener) o la tasa de mantener como fracción del precio (tasaMantenerPorcentaje)." }
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

// ── PL Entera (Branch & Bound) ──────────────────────────────────────────────
type TipoVariable = 'ENTERA' | 'BINARIA' | 'CONTINUA'

interface ModeloEntero {
  relajacion: ModeloLP                  // variables/objetivo/restricciones de la relajación
  tiposVariable: TipoVariable[]         // alineado por índice con relajacion.variables
}

interface SolucionEntera {
  valores: Record<string, number>       // solución entera óptima
  valorOptimo: number                   // Z* entero
  valorRelajacion: number               // óptimo LP de la raíz (para la brecha de integralidad)
  nodosExplorados: number
}

type AccionNodo = 'RAMIFICA' | 'INCUMBENTE' | 'PODA_COTA' | 'PODA_INFACTIBLE'

// datos de un paso de Branch & Bound (nodo del árbol o paso final)
interface EnteraStepDatos {
  // nodos del árbol
  nodoId?: number
  padreId?: number
  rama?: string
  estadoRelajacion?: string
  zRelajacion?: number
  valoresRelajacion?: Record<string, number>
  accion?: AccionNodo
  varRamificada?: string
  valorFraccionario?: number
  ramaIzquierda?: string
  ramaDerecha?: string
  // paso final
  valores?: Record<string, number>
  valorOptimo?: number
  valorRelajacion?: number
  brechaIntegralidad?: number
  nodosExplorados?: number
  status?: SolveStatus
}

// SolveResult<SolucionEntera>: reutiliza SolveStep pero con datos = EnteraStepDatos
interface EnteraStep { numero: number; titulo: string; descripcion: string; datos: EnteraStepDatos }
interface SolveResultEntera {
  status: SolveStatus
  solution: SolucionEntera | null
  steps: EnteraStep[]
}

// ── Inventarios (modelos deterministas) ─────────────────────────────────────
type MetodoInventario =
  | 'EOQ_BASICO' | 'EOQ_DESCUENTOS' | 'EOQ_FALTANTES'
  | 'PRODUCCION_ECONOMICA' | 'PUNTO_REORDEN'

interface TramoDescuento {
  cantidadMinima: number
  precioUnitario: number
}

// Body de los endpoints /api/v1/inventario/*. Solo se envían los campos que
// aplican al submodelo; el endpoint fuerza el metodo, así que es opcional.
interface ModeloInventario {
  metodo?: MetodoInventario
  demanda: number
  costoOrden: number
  costoMantener?: number          // todos menos descuentos-con-tasa
  costoFaltante?: number          // solo EOQ_FALTANTES
  tasaProduccion?: number         // solo PRODUCCION_ECONOMICA (P > demanda)
  leadTimeDias?: number           // solo PUNTO_REORDEN
  diasHabiles?: number            // solo PUNTO_REORDEN (def. 360)
  tasaMantenerPorcentaje?: number // solo EOQ_DESCUENTOS (fracción del precio)
  tramos?: TramoDescuento[]       // solo EOQ_DESCUENTOS
}

// Fila de la comparativa de EOQ con descuentos (una por tramo evaluado).
interface ComparativaTramo {
  precioUnitario: number
  cantidad: number
  costoTotal: number
  factible: boolean
}

// Record unificado: solo los campos del submodelo resuelto vienen non-null.
interface SolucionInventario {
  cantidadOptima: number                 // Q*
  costoTotalAnual: number                // en descuentos INCLUYE la compra
  costoOrdenarAnual: number
  costoMantenerAnual: number
  numeroPedidos: number | null           // N = D/Q*
  tiempoCicloDias: number | null         // T
  nivelMaximoInventario: number | null   // Imax (POQ) o S (faltantes)
  faltanteMaximo: number | null          // solo faltantes
  costoFaltanteAnual: number | null      // solo faltantes
  puntoReorden: number | null            // R, solo punto de reorden
  demandaDiaria: number | null           // d, solo punto de reorden
  costoCompraAnual: number | null        // D·C, solo descuentos
  precioUnitarioOptimo: number | null    // solo descuentos
  comparativa: ComparativaTramo[] | null // solo descuentos
  interpretacionPolitica: string
}

// datos de un paso de inventario (paso del cálculo, no iteración)
interface InventarioStepDatos {
  tipo: 'INVENTARIO'
  metodo: MetodoInventario
  formula?: string       // fórmula simbólica
  sustitucion?: string   // fórmula con números sustituidos
  resultado?: number     // valor computado en el paso
}

interface InventarioStep { numero: number; titulo: string; descripcion: string; datos: InventarioStepDatos }
interface SolveResultInventario {
  status: SolveStatus
  solution: SolucionInventario | null
  steps: InventarioStep[]
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

// Todos los campos tras `respuesta` son nullable — verificar siempre antes de usar.
// Como máximo UNO de los `resultado*` viene non-null en un mismo turno.
interface ChatResponse {
  sesionId: string
  respuesta: string
  modeloSugerido: ModeloLP | null              // non-null → pre-llenar formulario
  validacion: ValidacionResponse | null        // non-null → mostrar errores inline
  resultado: SolveResult | null                // non-null → tableau LP (Simplex/Gran M/Dos Fases)
  resultadoGrafico: SolveResult | null         // non-null → gráfico (2 variables)
  resultadoTransporte: SolveResult | null       // non-null → tabla de transporte
  resultadoRed: SolveResult | null              // non-null → grafo de redes
  resultadoEntero: SolveResultEntera | null     // non-null → árbol de Branch & Bound (PL Entera)
  resultadoInventario: SolveResultInventario | null // non-null → inventario (pasos + tarjeta de resultado)
  solicitudAprobacion: SolicitudAprobacion | null  // non-null → mostrar tarjeta Aprobar/Rechazar
}

// Human-in-the-Loop: el tutor quiere resolver y espera la aprobación del estudiante.
// El solver NO corre hasta que se envíe la decisión a POST /ai/chat/aprobacion.
type MetodoResolucion =
  | 'SIMPLEX' | 'GRAN_M' | 'DOS_FASES' | 'GRAFICO'
  | 'TRANSPORTE' | 'REDES' | 'BRANCH_AND_BOUND' | 'INVENTARIO'

interface SolicitudAprobacion {
  solicitudId: string
  metodo: MetodoResolucion
  // el modelo varía según el método: ModeloLP (LP/gráfico), ModeloTransporte,
  // ModeloRed, ModeloEntero (BRANCH_AND_BOUND) o ModeloInventario (INVENTARIO).
  // La UI decide cómo pintarlo según `metodo`.
  modelo: ModeloLP | ModeloEntero | ModeloInventario | Record<string, unknown>
}

interface DecisionAprobacionRequest {
  solicitudId: string
  aprobado: boolean
  comentario: string | null   // en un rechazo, explica qué corregir (re-alimenta al tutor)
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

// Resolver PL Entera — Branch & Bound
async function resolverBranchAndBound(modelo: ModeloEntero): Promise<SolveResultEntera> {
  const res = await fetch(`${API_BASE}/entera/branch-and-bound`, {
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

// Resolver Inventarios — un submodelo por endpoint (mismo body ModeloInventario)
type SubmodeloInventario =
  | 'eoq-basico' | 'eoq-descuentos' | 'eoq-faltantes'
  | 'produccion-economica' | 'punto-reorden'

async function resolverInventario(
  submodelo: SubmodeloInventario,
  modelo: ModeloInventario
): Promise<SolveResultInventario> {
  const res = await fetch(`${API_BASE}/inventario/${submodelo}`, {
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

// Decisión HITL sobre una solicitud de resolución pendiente.
// Devuelve un ChatResponse: si aprobó, con resultado/resultadoGrafico + explicación
// del tutor; si rechazó, normalmente con un modeloSugerido corregido.
async function decidirAprobacion(
  decision: DecisionAprobacionRequest
): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE}/ai/chat/aprobacion`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(decision),
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

2. **Los endpoints de solver son instantáneos** (Java puro, sin LLM): `/lp/*`, `/entera/*`,
   `/inventario/*`. Solo los endpoints `/ai/*` pasan por el LLM y tardan.

3. **El `sesionId` debe persistir en `sessionStorage`** (no `localStorage`) — se pierde
   cuando el usuario cierra la pestaña, que es el comportamiento esperado.

4. **Si el servidor se reinicia**, el `sesionId` guardado ya no servirá — captura el
   error y genera uno nuevo con `sesionId: null`.

5. **`modeloCorregido` puede ser `null`** en `ValidacionResponse` cuando el modelo es válido.
   Siempre verifica antes de usarlo.

6. **`solution` puede ser `null`** en `SolveResult` cuando `status` es `NO_ACOTADO` o
   `INFACTIBLE`. Siempre verifica antes de acceder a `solution.valores`.
