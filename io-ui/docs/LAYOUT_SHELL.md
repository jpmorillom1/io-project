# Layout shell — rail de módulos, toolbar y sistema visual

> Documenta el refactor de layout hecho sobre `io-ui` en esta iteración:
> pasar de una Home con grilla de tarjetas + páginas independientes por método,
> a un **shell fijo estilo IntelliJ** (rail de íconos + toolbar fusionados con el
> fondo) que envuelve un único workspace por módulo. Complementa (no reemplaza)
> `docs/FRONTEND_GUIDE.md` — esa guía describe el layout *planeado* originalmente
> (Home + split panel simple); este documento describe el layout *implementado*
> después del refactor.

---

## 1. Qué cambió y por qué

Antes: `/` era una Home con una grilla de 6 tarjetas de módulos, y cada método
(Simplex, Gráfico) vivía en su propia página con su propio layout completo
(`/lp`, `/lp/grafico`), sin nada persistente entre rutas.

Ahora: la app tiene un **shell persistente** (`AppShell`) que envuelve todas
las rutas, con dos franjas fijas —un rail vertical de íconos a la izquierda y
una toolbar horizontal arriba— que nunca se recargan al navegar. La pantalla
principal (`/`) ya no es un selector: redirige directo al workspace de LP
(chat + modelo), que es el mismo patrón que usará cada módulo futuro.

El método Gráfico de LP, que antes era la página independiente
`pages/lp/GraficoWorkspace.tsx` (formulario propio, sin chat), se fusionó
dentro del workspace único de LP: ahora es un resultado más que puede aparecer
junto al tableau, disparado desde el chat o desde un botón "Graficar" en el
mismo `ModelEditor`.

---

## 2. Estructura de archivos nuevos

```
io-ui/src/components/layout/
├── AppShell.tsx     ← shell raíz: define el fondo, arma la fila superior
│                        (logo + toolbar) y la fila inferior (rail + <Outlet/>)
├── ModuleRail.tsx   ← rail vertical de íconos, uno por módulo de IO
├── TopBar.tsx       ← toolbar horizontal: título + placeholder de cuenta
└── Logo.tsx         ← badge genérico "IO", esquina superior izquierda

io-ui/src/components/lp/
└── GraficoResultViewer.tsx   ← nuevo: vista de resultado gráfico (pasos,
                                  chart, tabla de vértices), reemplaza lo que
                                  antes vivía solo en GraficoWorkspace.tsx

io-ui/src/hooks/lp/
└── useGrafico.ts    ← nuevo: llama a resolverGrafico() y escribe
                         resultadoGrafico en el store (mismo patrón que useSimplex)
```

Archivos eliminados (ya no existen):
- `pages/Home.tsx` — la grilla de selección de módulos.
- `pages/lp/GraficoWorkspace.tsx` — fusionado dentro de `SimplexWorkspace.tsx`.

---

## 3. `AppShell` — estructura del shell

```
┌────┬──────────────────────────────────────────────┐
│ IO │  Plataforma IO                          (👤)  │  ← fila superior, h-9 (36px)
├────┼──────────────────────────────────────────────┤
│ Σ  │                                                │
│    │                                                │
│ 🚛 │           <Outlet /> → workspace del           │  ← fila inferior
│ 🕸 │              módulo activo (p.ej. LP)           │
│ ⚏  │                                                │
│ ⛓  │                                                │
│ 📦 │                                                │
└────┴──────────────────────────────────────────────┘
  44px               resto del ancho
```

`AppShell.tsx` es el único lugar donde se define el fondo real de la app (ver
sección 6). Todo lo demás (rail, toolbar, celda del logo, workspace) es
transparente para que ese fondo se vea continuo y sin costuras.

```tsx
<div className="h-screen flex flex-col overflow-hidden" style={{ background: /* ver §6 */ }}>
  <div className="flex h-9 shrink-0">
    <div style={{ width: '44px' }}><Logo /></div>
    <TopBar />
  </div>
  <div className="flex flex-1 overflow-hidden">
    <ModuleRail />
    <div className="flex-1 h-full overflow-hidden"><Outlet /></div>
  </div>
</div>
```

El ancho del rail (`44px`) y el alto de la toolbar (`h-9` = 36px) son
deliberadamente delgados — se redujeron desde un primer intento de 52px/44px
porque ocupaban demasiado espacio.

---

## 4. `ModuleRail` — navegación por íconos

Rail vertical de 44px. Un botón de ícono por módulo de IO, usando el mismo
array de datos que antes vivía en `Home.tsx`:

| Módulo | Ícono (`lucide-react`) | Ruta | Estado |
|---|---|---|---|
| Programación Lineal | `Sigma` | `/lp` | ✅ disponible |
| Transporte | `Truck` | `/transporte` | 🔒 bloqueado |
| Redes | `Network` | `/redes` | 🔒 bloqueado |
| PL Entera | `Binary` | `/pl-entera` | 🔒 bloqueado |
| Programación Dinámica | `Workflow` | `/prog-din` | 🔒 bloqueado |
| Inventarios | `Package` | `/inventario` | 🔒 bloqueado |

Todos los módulos se **renderizan siempre** (a diferencia de un primer intento
donde solo se mostraba el disponible), para comunicar el roadmap completo de
6 módulos. Los bloqueados:
- `disabled` en el `<button>`, `opacity: 0.35`, `cursor: not-allowed`.
- `title="{label} (próximamente)"` como tooltip nativo (no se agregó Radix
  Tooltip; no hacía falta para esto).
- No navegan al hacer clic (`onClick` no llama a `navigate` si `!disponible`).

El activo se resalta comparando `location.pathname.startsWith(m.path)`:
fondo `var(--ij-bg-hover)`, texto y borde izquierdo (`2px`) en
`var(--ij-teal)`.

**Cómo agregar un módulo nuevo cuando se implemente:** cambiar su
`disponible: false → true` en el array `MODULOS` de `ModuleRail.tsx` y crear
su ruta en `App.tsx`. No hace falta tocar nada más del shell.

---

## 5. `TopBar` + `Logo`

- **`Logo.tsx`**: badge de 24×24px (`h-6 w-6`), esquina redondeada `6px`,
  fondo `var(--ij-teal)`, texto "IO" en `#0B1211` (casi negro, para contraste),
  `JetBrains Mono` 10px bold. Es un logo genérico de placeholder — reemplazar
  cuando haya marca real.
- **`TopBar.tsx`**: título "Plataforma IO" a la izquierda; a la derecha, un
  botón deshabilitado con ícono `CircleUserRound` (`title="Cuenta y
  organización (próximamente)"`) — **el punto de extensión reservado** para
  cuando se agregue soporte de usuarios/empresas y su configuración.

---

## 6. Sistema visual — fondo, "fusión" y cards

### 6.1 El fondo es un único canvas

Antes, el rail/toolbar tenían su propio color sólido (`var(--ij-bg-editor)`,
más oscuro que el resto). Ahora **todo el shell comparte un solo fondo**,
definido una sola vez en `AppShell.tsx`:

```css
background:
  radial-gradient(1100px 700px at 0% 0%, rgba(20,196,182,0.09), transparent 55%),
  var(--ij-bg-secondary);
```

Esto es: el color base de toda la app (`--ij-bg-secondary`, `#282B30`) con un
resplandor sutil del mismo teal de marca (`--ij-teal`, `rgb(20,196,182)`) al
9% de opacidad, ancla do en la esquina superior izquierda y que se desvanece
a transparente hacia el 55% del radio del gradiente — el mismo efecto de "luz"
que tiene la Nueva UI de IntelliJ.

Para que este único fondo se vea sin costuras, **ningún hijo pinta su propio
color de fondo**: `ModuleRail`, `TopBar`, la celda del `Logo` y los
contenedores de `SimplexWorkspace` no tienen `background` propio — dejan pasar
el del `AppShell`. Antes tenían `background: var(--ij-bg-secondary)`
duplicado y a veces bordes divisorios (`border-right`, `border-bottom`); se
quitaron ambos para lograr la fusión.

### 6.2 Las cards son lo único que contrasta

Sobre ese canvas uniforme, los elementos que deben destacar (el chat, "Modelo
LP", el tableau, el gráfico) usan el color `var(--ij-bg-editor)` (más oscuro)
+ el mismo truco de `boxShadow: '0 0 0 1px var(--ij-bg-editor)'` que ya usa
el componente `Card` (`components/ui/card.tsx`) — así que el contraste entre
"fondo" y "card flotante" es el único lenguaje visual de jerarquía en la app.

El panel del chat (`SimplexWorkspace.tsx`) se convirtió explícitamente en una
card de este tipo — antes era un panel de borde a borde con esquinas
redondeadas solo del lado derecho (pegado al rail):

```tsx
<div
  className="w-[420px] shrink-0 flex flex-col overflow-hidden rounded-[10px]"
  style={{ background: 'var(--ij-bg-editor)', boxShadow: '0 0 0 1px var(--ij-bg-editor)' }}
>
  <ChatPanel />
</div>
```

### 6.3 Espaciado

- Entre el shell (rail/toolbar) y el chat: **0** — el chat toca directamente
  el borde del rail y de la toolbar (pedido explícito: "que se vean fusionadas
  con el fondo... separación mínima").
- Entre el chat y el panel de contenido (Modelo/Tableau/Gráfico): `gap-3`
  (12px) — sin este espacio, la card de "Modelo LP" quedaba pegada al chat y
  se veía desproporcionada.
- Padding interno del panel de contenido cuando hay resultado: `p-6` +
  `max-w-4xl` — evita que las cards se estiren de borde a borde en pantallas
  anchas (sin el `max-w`, en un monitor grande la card se veía "gigante").

---

## 7. Gráfico fusionado en el workspace de LP

`pages/lp/SimplexWorkspace.tsx` ahora puede mostrar **tableau y/o gráfico**
en la misma vista, sin cambiar de página:

- `resultado` (tableau) y `resultadoGrafico` (gráfico) viven ambos en
  `useWorkspaceStore`, y se muestran uno debajo del otro si están presentes
  (separados por `<Separator />`).
- El chat ya podía disparar ambos (`ChatResponse.resultado` /
  `ChatResponse.resultadoGrafico`, ver `hooks/shared/useChat.ts`) — eso no
  cambió.
- Lo nuevo es el **flujo manual**: `ModelEditor.tsx` agregó un botón
  "Graficar" junto a "Validar" y "Resolver →", habilitado solo si
  `modelo.variables.length === 2` (tooltip explicando la restricción si no).
  Usa el hook nuevo `useGrafico()` (mismo patrón que `useSimplex()`):

  ```ts
  function useGrafico(): {
    resolver: (modelo: ModeloLP) => Promise<void>
    isSolving: boolean
    error: string | null
  }
  ```

- La presentación del resultado gráfico (navegación de pasos con
  `ChevronLeft/Right`, tabla de vértices, mini-índice de pasos) se extrajo a
  `components/lp/GraficoResultViewer.tsx`, reutilizando `GraficoChart.tsx`
  sin cambios. Antes esa lógica de presentación solo existía dentro de
  `GraficoWorkspace.tsx` (ya eliminado).

---

## 8. Routing (`App.tsx`)

```tsx
<Routes>
  <Route element={<AppShell />}>
    <Route index element={<Navigate to="/lp" replace />} />
    <Route path="/lp" element={<SimplexWorkspace />} />
  </Route>
</Routes>
```

`/` ya no renderiza nada por sí sola: redirige a `/lp`. Cuando se implemente
un módulo nuevo, su ruta se agrega como hija de `AppShell` igual que `/lp`, y
su ícono en `ModuleRail` pasa a `disponible: true`.

---

## 9. Referencias cruzadas

- `docs/FRONTEND_GUIDE.md` — sigue vigente para: stack de UI, arquitectura de
  capas (`pages → hooks → api/store → types`), contratos de los hooks de LP,
  estados de `WorkspaceStatus` y flujos de usuario A/B/C. Su sección "Layout
  general" y "1. Home — selector de módulo" describen el diseño *previo* a
  este refactor y quedan reemplazadas por este documento.
- `io-api/docs/FRONTEND_INTEGRATION.md` — contratos de API que consume todo
  lo anterior.
