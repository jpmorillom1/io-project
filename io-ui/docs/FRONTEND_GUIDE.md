# Guía de diseño Frontend — io-ui

> Referencia para el agente React que va a construir la interfaz.
> Stack decidido: React + Vite (en `io-ui/`).
> La API que consume está documentada en `io-api/docs/FRONTEND_INTEGRATION.md`.
>
> ⚠️ Las secciones **"Layout general"** y **"1. Home — selector de módulo"**
> describen el diseño original, ya reemplazado por el shell con rail de
> íconos + toolbar. Ver `docs/LAYOUT_SHELL.md` para el layout implementado.
> El resto de este documento (stack, arquitectura de capas, hooks, estados,
> flujos de usuario) sigue vigente.

---

## Stack de UI — decisión y justificación

**shadcn/ui + Tailwind CSS** es el stack elegido. No usar MUI ni Chakra.

**Por qué no MUI:** impone Material Design visualmente sobre todo el proyecto. El
`TableauViewer` necesita control total sobre celdas individuales con clases condicionales
(columna entrante, fila pivote, celda intersección) — con MUI eso implica pelear contra
`sx` props y overrides. Con Tailwind es trivial.

**Por qué shadcn/ui + Tailwind:**
- Las clases condicionales del tableau (`bg-blue-50`, `bg-yellow-50`, `bg-orange-200`)
  son el núcleo del componente estrella — Tailwind lo hace sin fricción.
- shadcn provee los componentes de soporte necesarios (Badge, Card, Skeleton, Tooltip,
  Separator) sin imponer una identidad visual encima del diseño propio.
- El resultado se siente como una herramienta académica/técnica, no una app de Google.

| Necesidad | Herramienta |
|-----------|-------------|
| UI base | **shadcn/ui** + **Tailwind CSS** |
| Estado global | **Zustand** |
| Formularios | **React Hook Form** + **Zod** |
| Fórmulas matemáticas | **KaTeX** |
| HTTP / cache | **TanStack Query** |
| Router | **React Router v7** |

---

## Layout general

Un **split asimétrico colapsable** de dos paneles: el tutor fijo a la izquierda,
el área de trabajo a la derecha dividida en tres zonas verticales secuenciales.

```
┌─────────────────────────────────────────────────────────────────┐
│  IO Platform          [LP] [Transporte] [Redes] ...             │  ← navbar
├────────────┬────────────────────────────────────────────────────┤
│            │                                                     │
│  TUTOR Ío  │  ① ENUNCIADO                                       │
│            │  ┌─────────────────────────────────────────────┐   │
│            │  │ textarea...                                  │   │
│  mensajes  │  └────────────────────────── [Sugerir →] ──────┘   │
│  ...       │                                                     │
│            │  ② MODELO LP                                       │
│            │  MAX  5·x1  +  4·x2                               │
│            │  s.a. restricciones...                             │
│            │                       [Validar]  [Resolver →]      │
│            │                                                     │
│            ├─────────────────── divider ─────────────────────── │
│            │                                                     │
│  ───────   │  ③ TABLEAU  (aparece al resolver)                  │
│  [input ]  │  Paso 2/3 — Iteración 1         [◀] [▶]           │
│  [Enviar]  │  ┌─────────────────────────────────────────────┐   │
│            │  │   tabla resaltada                            │   │
│            │  └─────────────────────────────────────────────┘   │
└────────────┴────────────────────────────────────────────────────┘
```

**Reglas del layout:**
- El panel izquierdo (Tutor Ío) tiene ancho fijo y no hace scroll horizontal.
- El panel derecho hace scroll vertical — las tres zonas son secuenciales.
- El Tableau (zona ③) está separado visualmente de la zona ② con un divider o
  Card de fondo diferente. No es un simple `div` más abajo — es una zona propia
  porque es el diferenciador visual del proyecto.
- El panel izquierdo y el derecho son **independientes pero se retroalimentan**:
  el tutor puede pre-llenar el formulario y el estudiante puede pedir al tutor
  que explique lo que ve en el tableau.

---

## Las 3 pantallas principales

### 1. Home — selector de módulo

Grilla de 6 tarjetas, una por módulo de IO. Los módulos pendientes muestran un
badge "Próximamente" y están deshabilitados. Solo LP Simplex es navegable por ahora.

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│    LP    │  │Transporte│  │  Redes   │
│ Simplex  │  │          │  │          │
│          │  │  🔒 soon │  │  🔒 soon │
└──────────┘  └──────────┘  └──────────┘
┌──────────┐  ┌──────────┐  ┌──────────┐
│PL Entera │  │ Prog.Din.│  │Inventario│
│  🔒 soon │  │  🔒 soon │  │  🔒 soon │
└──────────┘  └──────────┘  └──────────┘
```

### 2. Workspace LP Simplex

El núcleo de la app. Tres zonas verticales en el panel derecho:

**Zona ① — Enunciado libre**
- `<textarea>` con altura auto-expand donde el estudiante escribe o pega el enunciado.
- Botón "Sugerir modelo" → llama a `POST /api/v1/ai/sugerir-modelo`.
- Spinner mientras espera (la llamada al LLM puede tardar 2-8s).
- Al recibir la respuesta, pre-llena el formulario de la Zona ②.

**Zona ② — Editor del modelo**

El formulario se diseña para que parezca matemático, no una tabla de datos:

```
  OBJETIVO:  [MAX ▾]   [5] · x1  +  [4] · x2

  RESTRICCIONES
  ┌──────────────────────────────────────────────────────┐
  │  [6]·x1  +  [4]·x2  [≤ ▾]  [24]            [🗑]   │
  │  [1]·x1  +  [2]·x2  [≤ ▾]  [ 6]            [🗑]   │
  └──────────────────────────────────────────────────────┘
  [+ Agregar restricción]

                                  [Validar]  [Resolver →]
```

- Los coeficientes son campos numéricos inline con los operadores matemáticos entre
  ellos (`·`, `+`, `≤`), no una tabla de celdas tipo spreadsheet.
- Chips editables para los nombres de variables (`x1`, `x2`, + agregar).
- Selector `MAXIMIZAR / MINIMIZAR` en el objetivo.
- Botón "Validar" → llama a `POST /api/v1/ai/validar-modelo`:
  - `esValido: false` → errores aparecen inline bajo cada campo.
  - `esValido: true` → botón "Resolver" se habilita.
- Botón "Resolver" deshabilitado hasta validar → llama a `POST /api/v1/lp/simplex`.

**Zona ③ — Visualizador de tableau**

Zona separada visualmente (Card o fondo diferente + divider). Aparece solo al resolver.
Ver sección "Visualizador de tableau" más abajo.

### 3. Visualizador de tableau paso a paso

El componente estrella del proyecto. El estudiante navega iteración por iteración.

```
  Paso 2 / 3 — Iteración 1: entra x1, sale s1    [◀ Anterior] [Siguiente ▶]

         x1      x2      s1      s2      b
   s1  [ 6.0    4.0     1.0     0.0    24.0 ]   ← fila pivote (amarillo)
   s2  [ 1.0    2.0     0.0     1.0     6.0 ]
    z  [-5.0   -4.0     0.0     0.0     0.0 ]   ← fila z (gris, siempre)
         ↑
    columna pivote (azul)

  📌 Variable entrante: x1  |  Variable saliente: s1  |  Pivote: (fila 1, col x1)
```

**Sistema de colores del tableau:**

| Elemento | Estilo Tailwind de referencia |
|---|---|
| Columna entrante | `bg-blue-50` + borde azul en encabezado |
| Fila saliente | `bg-yellow-50` + borde izquierdo amarillo |
| Celda pivote (intersección) | `bg-orange-200` + texto bold |
| Fila z (última, siempre) | `bg-slate-100` + texto itálico |
| Solución óptima (paso final) | banner `bg-green-50` con `Z* = 21.0`, `x1 = 3`, `x2 = 1.5` |
| Infactible / No acotado | banner `bg-red-50` con mensaje explicativo |

Una `<table>` bien estilizada con Tailwind es suficiente — no se necesita librería
de gráficos ni de tablas externas.

---

## Arquitectura de capas

La separación de responsabilidades sigue este orden estricto.
Ninguna capa puede saltarse la que tiene debajo.

```
┌─────────────────────────────────────────┐
│           pages / components            │  solo render + eventos locales
│   usan hooks, nunca api ni store directo│
├─────────────────────────────────────────┤
│              hooks/                     │  lógica + efectos + coordinación
│   usan api/ y store/, orquestan todo   │
├──────────────┬──────────────────────────┤
│    store/    │         api/             │  estado global  |  fetch puro
│   Zustand    │   wrappers tipados       │  sin lógica UI  |  sin estado
├──────────────┴──────────────────────────┤
│              types/                     │  contratos TypeScript compartidos
└─────────────────────────────────────────┘
```

**Regla clave:** los componentes nunca llaman a `api/` directamente ni escriben en
el store directamente. Todo pasa por un hook. Esto mantiene los componentes testeables
y hace que añadir un nuevo módulo de IO sea replicar el patrón de hooks, no reescribir
componentes.

---

## Estructura de carpetas

```
io-ui/src/
│
├── types/
│   └── io.ts                        ← todos los tipos TypeScript del contrato de API
│                                       (ModeloLP, SolveResult, ChatResponse, etc.)
│
├── api/
│   └── io.ts                        ← los 4 fetch wrappers tipados, sin estado,
│                                       sin lógica de UI. Solo HTTP + tipado.
│
├── store/
│   └── useWorkspaceStore.ts         ← estado global: sesionId, modelo, resultado,
│                                       WorkspaceStatus. Nunca llama a api/ directamente.
│
├── hooks/
│   ├── lp/
│   │   ├── useSimplex.ts            ← llama a api/resolverSimplex, actualiza store
│   │   ├── useSugerirModelo.ts      ← llama a api/sugerirModelo, pre-llena modelo en store
│   │   ├── useValidarModelo.ts      ← llama a api/validarModelo, maneja errores inline
│   │   └── useModeloForm.ts         ← lógica del formulario dinámico: agregar/quitar
│   │                                   variables y restricciones, sincroniza con store
│   ├── shared/
│   │   ├── useChat.ts               ← sesionId, historial de mensajes, turno activo,
│   │   │                               recuperación si el servidor se reinicia
│   │   └── useTableauNavigation.ts  ← paso actual, ir a anterior/siguiente,
│   │                                   cálculo de highlights (pivote, columna, fila)
│   └── index.ts                     ← re-exporta todos los hooks (barrel)
│
├── components/
│   ├── chat/
│   │   ├── ChatPanel.tsx            ← panel izquierdo completo; usa useChat
│   │   └── ChatBubble.tsx           ← burbuja individual (user / tutor / loading)
│   │
│   ├── lp/
│   │   ├── ProblemInput.tsx         ← textarea + botón "Sugerir modelo"; usa useSugerirModelo
│   │   ├── ModelEditor.tsx          ← formulario dinámico; usa useModeloForm + useValidarModelo
│   │   ├── ValidationFeedback.tsx   ← errores inline; recibe erroresValidacion como prop
│   │   ├── TableauViewer.tsx        ← navegación de pasos; usa useTableauNavigation
│   │   ├── TableauTable.tsx         ← tabla pura: recibe un SolveStep + highlights como props.
│   │   │                               No tiene estado propio. Testeable de forma aislada.
│   │   └── ResultBanner.tsx         ← Z* y valores (OPTIMO) o aviso (INFACTIBLE/NO_ACOTADO)
│   │
│   └── ui/                          ← componentes shadcn (auto-generados, no editar)
│
├── pages/
│   ├── Home.tsx                     ← grilla de 6 módulos; sin lógica, solo navegación
│   └── lp/
│       └── SimplexWorkspace.tsx     ← layout split panel; orquesta zonas ①②③ y ChatPanel
│
└── lib/
    └── utils.ts                     ← helpers de Tailwind (cn), formateadores numéricos, etc.
```

---

## Custom hooks — contratos

### `hooks/lp/useSimplex.ts`
```typescript
// Llama a POST /api/v1/lp/simplex y actualiza el store.
// El componente solo llama a resolver() y lee isSolving / error.
function useSimplex(): {
  resolver: (modelo: ModeloLP) => Promise<void>
  isSolving: boolean
  error: string | null
}
```

### `hooks/lp/useSugerirModelo.ts`
```typescript
// Llama a POST /api/v1/ai/sugerir-modelo y pre-llena modelo en el store.
function useSugerirModelo(): {
  sugerir: (descripcion: string) => Promise<void>
  isSuggesting: boolean
  advertencias: string[]
  error: string | null
}
```

### `hooks/lp/useValidarModelo.ts`
```typescript
// Llama a POST /api/v1/ai/validar-modelo.
// Expone errores para mostrar inline y modeloCorregido si la IA lo sugiere.
function useValidarModelo(): {
  validar: (descripcion: string, modelo: ModeloLP) => Promise<void>
  isValidating: boolean
  errores: string[]
  modeloCorregido: ModeloLP | null
  error: string | null
}
```

### `hooks/lp/useModeloForm.ts`
```typescript
// Lógica del formulario dinámico: agregar/quitar variables y restricciones.
// Sincroniza el modelo en el store al cambiar cualquier campo.
// Cuando el modelo cambia después de SOLVED, resetea el resultado.
function useModeloForm(): {
  modelo: ModeloLP
  agregarVariable: () => void
  quitarVariable: (index: number) => void
  agregarRestriccion: () => void
  quitarRestriccion: (index: number) => void
  setCoeficienteObjetivo: (index: number, valor: number) => void
  setTipoObjetivo: (tipo: TipoObjetivo) => void
  setCoeficienteRestriccion: (fila: number, col: number, valor: number) => void
  setRhs: (index: number, valor: number) => void
  setTipoRestriccion: (index: number, tipo: TipoRestriccion) => void
}
```

### `hooks/shared/useChat.ts`
```typescript
// Maneja la sesión socrática completa.
// Persiste sesionId en sessionStorage.
// Si el servidor se reinicia, detecta el error y genera nueva sesión.
//
// IMPORTANTE — el chat es el orquestador principal:
// cuando ChatResponse incluye modeloSugerido / validacion / resultado,
// este hook escribe directamente en el store para que la UI se actualice
// automáticamente sin que el estudiante haga nada extra.
function useChat(): {
  mensajes: Mensaje[]
  enviar: (texto: string) => Promise<void>
  isSending: boolean
  error: string | null
}
```

**Lógica interna de `useChat` al recibir cada respuesta:**
```typescript
const respuesta = await api.chat(sesionId, texto)

// El tutor puede actualizar el formulario sin que el usuario haga nada:
if (respuesta.modeloSugerido) {
  store.setModelo(respuesta.modeloSugerido)
  store.setStatus('EDITING')
}
if (respuesta.validacion) {
  store.setValidacion(respuesta.validacion)
  store.setStatus(respuesta.validacion.esValido ? 'EDITING' : 'INVALID')
}
if (respuesta.resultado) {
  store.setResultado(respuesta.resultado)
  store.setStatus('SOLVED')
}
// Siempre agrega el texto al historial de mensajes:
agregarMensaje({ rol: 'tutor', texto: respuesta.respuesta })
```

### `hooks/shared/useTableauNavigation.ts`
```typescript
// Recibe los steps del solver y maneja la navegación.
// Calcula qué celdas resaltar en el paso actual.
function useTableauNavigation(steps: SolveStep[]): {
  pasoActual: SolveStep
  numeroPaso: number        // 1-based para mostrar al usuario
  totalPasos: number
  puedeAnterior: boolean
  puedeSiguiente: boolean
  irAnterior: () => void
  irSiguiente: () => void
  highlights: TableauHighlights
}

interface TableauHighlights {
  columnaEntrada: number | null   // índice de columna a resaltar en azul
  filaSalida: number | null       // índice de fila a resaltar en amarillo
  celdaPivote: [number, number] | null  // [fila, col] a resaltar en naranja
}
```

---

## Estado global — `useWorkspaceStore`

El store maneja un **enum de estado explícito**, no múltiples booleanos sueltos
(`isLoading`, `isValidated`, etc.) que pueden combinarse en estados imposibles.

```typescript
type WorkspaceStatus =
  | 'IDLE'           // sin actividad, formulario vacío
  | 'SUGGESTING'     // esperando respuesta de /ai/sugerir-modelo
  | 'EDITING'        // formulario con datos, pendiente de validar
  | 'VALIDATING'     // esperando respuesta de /ai/validar-modelo
  | 'INVALID'        // validación fallida, errores visibles
  | 'SOLVING'        // esperando respuesta de /lp/simplex
  | 'SOLVED'         // resultado visible, tableau navegable

interface WorkspaceStore {
  status: WorkspaceStatus
  sesionId: string | null
  descripcionProblema: string
  modelo: ModeloLP | null
  erroresValidacion: string[]
  resultado: SolveResult | null
}
```

**Transiciones válidas:**

```
IDLE
  → [escribe enunciado + clic Sugerir]  → SUGGESTING
  → [llena formulario manualmente]      → EDITING

SUGGESTING
  → [respuesta llega]   → EDITING
  → [error de red]      → IDLE

EDITING
  → [clic Validar]      → VALIDATING
  → [clic Resolver]     → SOLVING   (flujo directo sin validar)

VALIDATING
  → [esValido: false]   → INVALID
  → [esValido: true]    → EDITING   (con botón Resolver habilitado)

INVALID
  → [edita campos]      → EDITING

SOLVING
  → [resultado llega]   → SOLVED

SOLVED
  → [edita el modelo]   → EDITING   (resetea resultado)
```

---

## Escalabilidad — cómo añadir un nuevo módulo de IO

Cuando se implemente Transporte, Redes u otro módulo, el patrón a seguir es:

```
1. Añadir los tipos en types/io.ts  (ModeloTransporte, SolveResultTransporte, etc.)
2. Añadir el wrapper en api/io.ts   (resolverTransporte, etc.)
3. Crear hooks/transporte/           (useTransporte, useTablaTransporte, etc.)
4. Crear components/transporte/      (usando los mismos shadcn/Tailwind)
5. Crear pages/transporte/           (WorkspaceTransporte)
6. Añadir la ruta en el router
```

El store puede crecer con slices por módulo (Zustand lo soporta con `create` +
`slices`) sin romper lo existente. Los hooks de `shared/` (`useChat`,
`useTableauNavigation`) son reutilizables por todos los módulos sin modificación.

---

## Flujos de usuario

### Flujo A — chat socrático (principal y recomendado)

El chat es el **entrypoint y orquestador principal**. El tutor Ío no solo responde
en texto: cuando toma decisiones (sugerir un modelo, validarlo, resolverlo), el backend
devuelve datos estructurados en `ChatResponse` y el formulario + tableau se actualizan
automáticamente.

```
1. Estudiante escribe en el chat: "Tengo un problema de producción de mesas y sillas..."
         │
         ▼
2. POST /api/v1/ai/chat → ChatResponse {
     respuesta: "Bien, identifiqué tu modelo. Verifícalo en el formulario →",
     modeloSugerido: { variables, objetivo, restricciones }   ← formulario se pre-llena
   }
         │
         ▼
3. Estudiante revisa el formulario (que ya tiene datos), corrige si quiere,
   o simplemente responde en el chat: "sí, está bien"
         │
         ▼
4. POST /api/v1/ai/chat → ChatResponse {
     respuesta: "Perfecto. El modelo es correcto. ¿Quieres que lo resuelva?",
     validacion: { esValido: true, ... }   ← feedback inline en el formulario
   }
         │
         ▼
5. Estudiante: "sí, resuélvelo"
         │
         ▼
6. POST /api/v1/ai/chat → ChatResponse {
     respuesta: "Z* = 21. En la iteración 1 entró x1 porque...",
     resultado: { status: 'OPTIMO', steps: [...] }   ← tableau aparece automáticamente
   }
         │
         ▼
7. Estudiante navega el tableau y puede preguntar:
   "¿por qué entró x1 primero y no x2?"
   → el tutor explica la regla de Dantzig usando el detalle de las iteraciones
```

### Flujo B — formulario directo (sin IA)

```
1. Estudiante llena el formulario manualmente
2. Clic "Resolver" directamente  →  POST /api/v1/lp/simplex
3. Tableau aparece en Zona ③. Sin explicación del tutor.
```

### Flujo C — mixto (formulario + chat)

```
- El estudiante usa el formulario para ingresar el modelo
- Hace clic en "Validar con IA"  →  POST /api/v1/ai/chat con mensaje interno:
  "Valida este modelo: [modelo formateado como texto]"
- El tutor responde + registrarValidacion → errores aparecen inline
- Si válido, clic "Resolver"  →  POST /api/v1/lp/simplex
```

---

## Responsive

- `≥ 768px`: split panel (chat izquierda | trabajo derecha).
- `< 768px`: tres tabs en la parte superior — **"Tutor" | "Modelo" | "Resultado"**.
  En mobile el split no es usable; los tabs colapsan las tres secciones.

---

## Consideraciones de UX

- **El chat es el driver principal.** El formulario reacciona al chat, no al revés.
  Cuando `ChatResponse.modeloSugerido` llega, el formulario se actualiza sin que el
  usuario haga clic en nada. Lo mismo con `validacion` y `resultado`.

- **Latencia del chat:** `/ai/chat` puede tardar 2-8s (Groq + LLM). Muestra un indicador
  de "escribiendo..." en el panel del tutor. El formulario queda deshabilitado mientras
  se espera, para evitar ediciones que se pisoteen con la respuesta del tutor.

- **`/lp/simplex` es instantáneo** (Java puro, sin LLM). El botón "Resolver directo"
  del formulario puede usarse sin esperar al chat.

- **`sesionId` en `sessionStorage`**, no `localStorage`. Se pierde al cerrar la pestaña,
  que es el comportamiento esperado para una sesión de estudio.

- **Si el servidor se reinicia**, el `sesionId` guardado ya no tiene memoria asociada.
  Detecta el error y genera nueva sesión con `sesionId: null`. Responsabilidad de `useChat`.

- **`ChatResponse` campos nullables:** verifica siempre antes de usar:
  - `modeloSugerido` → puede ser `null` (respuesta conversacional pura)
  - `validacion` → puede ser `null`
  - `resultado` → puede ser `null`
  - `resultado.solution` → puede ser `null` si `status` es `NO_ACOTADO` o `INFACTIBLE`

- **Si el estudiante edita el modelo después de haber resuelto**, el resultado y el
  tableau se resetean automáticamente (responsabilidad de `useModeloForm`).
  No mostrar un tableau desactualizado.

- **Transparencia del chat:** cuando el tutor actualiza el formulario automáticamente,
  muestra un indicador visual sutil en el formulario ("actualizado por Ío ✓") para que
  el estudiante sepa qué cambió y por qué, y no se sienta desorientado.

---

## Prioridad de implementación (primer sprint)

| Orden | Archivo | Valor |
|-------|---------|-------|
| 1 | `types/io.ts` | Base de tipado para todo lo demás |
| 2 | `api/io.ts` | Desbloquea todos los hooks |
| 3 | `store/useWorkspaceStore.ts` | Estado central con WorkspaceStatus |
| 4 | `hooks/lp/useModeloForm.ts` | Lógica del formulario antes de renderizarlo |
| 5 | `hooks/lp/useSimplex.ts` | Conecta el solver con el store |
| 6 | `hooks/shared/useTableauNavigation.ts` | Navegación + highlights del tableau |
| 7 | `components/lp/ModelEditor.tsx` | Formulario dinámico |
| 8 | `components/lp/TableauTable.tsx` + `TableauViewer.tsx` | Componente estrella |
| 9 | `hooks/lp/useSugerirModelo.ts` + `useValidarModelo.ts` | Flujo IA |
| 10 | `components/lp/ProblemInput.tsx` + `ValidationFeedback.tsx` | UI del flujo IA |
| 11 | `hooks/shared/useChat.ts` | Sesión socrática |
| 12 | `components/chat/ChatPanel.tsx` + `ChatBubble.tsx` | Panel del tutor |
| 13 | `pages/lp/SimplexWorkspace.tsx` | Integra todo en el layout split |
| 14 | `pages/Home.tsx` | Entry point |

---

## Referencia de API

Todos los tipos TypeScript y ejemplos de request/response están en:
```
io-api/docs/FRONTEND_INTEGRATION.md
```