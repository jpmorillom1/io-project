# Módulo Transporte (COMPLETADO)

Implementado de punta a punta: dominio → aplicación → REST → tool IA con HITL → frontend
(editor, pasos, banner y **grafo de red**). Sirve de plantilla para Redes (ver `GUIA_REDES.md`).

## Métodos
- **Soluciones iniciales**: Esquina Noroeste, Costo Mínimo, Vogel (VAM).
- **MODI** (óptimo): corre los 3 iniciales, reporta el costo de cada uno, **arranca del más
  barato** y optimiza con multiplicadores u/v + ciclo stepping-stone; maneja degeneración
  con celdas ε (union-find). ("MODI valida cuál es mejor".)
- **Balanceo automático**: si Σoferta ≠ Σdemanda, `Balanceador` agrega un origen/destino
  `"Ficticio"` de costo 0.
- Húngaro (asignación) quedó **fuera de alcance** (pendiente).

## Backend
- `domain/transporte/` — records + solvers pure-Java por sub-paquete + `Balanceador`,
  `TransporteUtils`, `modi/CicloSteppingStone`. `ModeloTransporte implements ModeloResoluble`.
- `application/transporte/TransporteService` — fachada única, despacha por `modelo.metodo()`.
- `infrastructure/transporte/TransporteController` — `POST /api/v1/transporte/{esquina-noroeste,
  costo-minimo,vogel,modi}`. Contrato JSON en `API_CONTRACT.md`.

## Decisiones de diseño reutilizables
- **`domain/common/ModeloResoluble`** — interfaz marcador; generalizó TODA la cadena HITL de
  `ModeloLP` a `ModeloResoluble`, para que cualquier módulo entre sin re-tipar.
- **`MetodoResolucion.TRANSPORTE`** — un solo valor de enum; el submétodo viaja dentro del
  `ModeloTransporte`. `ResolucionEjecutor.Ejecucion` tiene 3 resultados (solo uno non-null).
- **Gotcha LangChain4j 1.13.0** — parámetros `@Tool` no admiten genéricos anidados; la matriz
  de costos va como `List<FilaCostos>` (record), no `List<List<Double>>`. Ver `CLAUDE.md §9`.

## Frontend (`io-ui/`)
- Estilo alineado a LP: `TransporteModelEditor` (≈ ModelEditor), `TransporteResultViewer`
  (≈ TableauViewer), `TransporteStepTable` (≈ TableauTable), `TransporteResultBanner` (≈ ResultBanner).
- **Workspace adaptativo**: el chat vive en `context/ChatProvider` (montado en `AppShell`, sobrevive
  a la navegación). `moduloDeRespuesta()` detecta LP vs Transporte en cada respuesta y auto-navega
  a `/lp` o `/transporte`.
- **Grafo de red**: `components/shared/NetworkGraph.tsx` (SVG genérico, **reutilizable por Redes**)
  + adaptador `lib/transporte/construirGrafo.ts` + `TransporteGrafo` (toggle Modelo/Resolución,
  botón "Graficar" en el editor). El mismo componente muestra el modelo (rutas+costos) y la
  resolución (rutas usadas con flujo).

## Verificación
- 26 tests backend verdes (dominio, controller/Jackson, tool-schema, HITL end-to-end).
- `npm run build` limpio.
- `contextLoads()` falla sin `GROQ_API_KEY` (pre-existente, no relacionado).
