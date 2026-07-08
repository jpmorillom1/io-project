# Módulo Inventarios (deterministas) — COMPLETADO (backend)

Modelos deterministas de inventario con demanda conocida. Cinco submodelos bajo un único
módulo `inventario`, con el patrón multi-método de Transporte/Redes. El frontend queda pendiente.

## Naturaleza

A diferencia de LP/Transporte/Redes, los modelos de inventario son **fórmulas cerradas**, no
algoritmos iterativos:

- `status` siempre `OPTIMO` con datos válidos. Una entrada malformada (D≤0, H≤0, P≤D en POQ,
  tramos vacíos en descuentos, etc.) es `IllegalArgumentException` → HTTP 400. **No existe**
  resultado "infactible/no acotado" en inventarios deterministas.
- `steps` no son iteraciones sino los **pasos del cálculo** (parámetros → fórmula → sustitución →
  resultado → interpretación). Cada paso lleva `datos` con `tipo="INVENTARIO"`, `formula`,
  `sustitucion` y `resultado`.

## Submodelos y fórmulas (Taha)

| Submodelo (`MetodoInventario`) | Fórmula clave | Parámetros extra |
|---|---|---|
| `EOQ_BASICO` | Q\* = √(2DK/H); N=D/Q\*; CT=√(2DKH) | — |
| `PRODUCCION_ECONOMICA` (POQ/EPQ) | Q\* = √(2DK/(H(1−D/P))); Imax=Q\*(1−D/P) | `tasaProduccion` P>D |
| `EOQ_FALTANTES` | Q\* = √(2DK/H)·√((H+b)/b); S=Q\*·b/(H+b); faltante=Q\*−S | `costoFaltante` b |
| `PUNTO_REORDEN` | Q\* = √(2DK/H); d=D/díasHábiles; R=d·L (menos ciclos completos) | `leadTimeDias` L, `diasHabiles` (def. 360) |
| `EOQ_DESCUENTOS` | por tramo j: H_j=i·C_j; Q_j ajustado al rango; CT_j=D·C_j+(D/Q)K+(Q/2)H_j; gana el menor CT factible | `tramos` (tabla precio), `tasaMantenerPorcentaje` i **o** `costoMantener` H |

Donde D=demanda, K=costo de ordenar/preparar, H=costo de mantener por unidad-periodo.

## Endpoints

Cada endpoint fuerza su método; el cuerpo es un `ModeloInventario`; devuelve `SolveResult<SolucionInventario>`.

```
POST /api/v1/inventario/eoq-basico           { "demanda":1000, "costoOrden":50, "costoMantener":4 }
POST /api/v1/inventario/produccion-economica { "demanda":1000, "costoOrden":50, "costoMantener":4, "tasaProduccion":2000 }
POST /api/v1/inventario/eoq-faltantes        { "demanda":1000, "costoOrden":50, "costoMantener":4, "costoFaltante":10 }
POST /api/v1/inventario/punto-reorden        { "demanda":1200, "costoOrden":40, "costoMantener":3, "leadTimeDias":10, "diasHabiles":360 }
POST /api/v1/inventario/eoq-descuentos       { "demanda":5000, "costoOrden":49, "tasaMantenerPorcentaje":0.2,
                                               "tramos":[{"cantidadMinima":0,"precioUnitario":5.0},
                                                         {"cantidadMinima":1000,"precioUnitario":4.8},
                                                         {"cantidadMinima":2500,"precioUnitario":4.75}] }
```

`SolucionInventario` es un record unificado (campos null según submodelo): `cantidadOptima` (Q\*),
`costoTotalAnual` (en descuentos INCLUYE la compra), `costoOrdenarAnual`, `costoMantenerAnual`,
`numeroPedidos`, `tiempoCicloDias`, `nivelMaximoInventario` (Imax/S), `faltanteMaximo`,
`costoFaltanteAnual`, `puntoReorden`, `demandaDiaria`, `costoCompraAnual`, `precioUnitarioOptimo`,
`comparativa` (por tramo), `interpretacionPolitica`.

## Capa de IA (HITL)

- `MetodoResolucion.INVENTARIO` agrupa los 5 submodelos (el submodelo viaja dentro del `ModeloInventario`).
- Dos `@Tool` en `InventarioTool` (patrón `RedTool`, sin genéricos anidados):
  - `resolverInventario(metodo, demanda, costoOrden, costoMantener, costoFaltante?, tasaProduccion?, leadTimeDias?, diasHabiles?)`
  - `resolverInventarioDescuentos(demanda, costoOrden, tramos, tasaMantenerPorcentaje?, costoMantener?)`
  - Los opcionales van `@P(required=false)`/`@JsonProperty(required=false)` con instrucción de OMITIR (nunca null).
- `ResolucionEjecutor.formatearInventario(...)` genera el resumen para el tutor (Q\*, costos, política,
  campos por submodelo y guía socrática del trade-off ordenar-vs-mantener).
- `ChatResponse.resultadoInventario` lleva el resultado a la UI tras la aprobación humana.

## Valores de referencia (para tests)

- EOQ básico D=1000,K=50,H=4 → Q\*=158.11, N=6.32, CT=632.46, ordenar=mantener=316.23.
- POQ +P=2000 → Q\*=223.61, Imax=111.80, CT=447.21.
- Faltantes +b=10 → Q\*=187.08, S=133.63, faltante=53.45, CT=534.52 (S+faltante=Q\*).
- Reorden D=1200,K=40,H=3,L=10,360d → Q\*=178.89, d=3.33, R=33.33.
- Descuentos D=5000,K=49,i=0.2, tramos {5.0 / 4.8@1000 / 4.75@2500} → gana 4.8 con Q=1000, CT=24725.
