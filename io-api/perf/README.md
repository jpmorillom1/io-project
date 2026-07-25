# Pruebas de estrés — solvers de IO (k6)

Carga sobre los **24 endpoints deterministas** de resolución. Los endpoints `/ai/*`
quedan **fuera a propósito**: dependen de Groq y ChromaDB, así que medirían la latencia
de un tercero en vez de la del backend — y quemarían cuota de tokens en cada iteración.

| Archivo | Qué es |
|---|---|
| `stress-solvers.js` | Script principal: catálogo de endpoints, escenarios, checks y thresholds |
| `payloads.js` | Generadores de instancias válidas y factibles por módulo |

## Uso

```bash
cd io-api/perf

k6 run -e SCENARIO=smoke stress-solvers.js                 # valida los 24 payloads (~13 s)
k6 run stress-solvers.js                                   # estrés por defecto (~10 min)
k6 run -e SCENARIO=stress -e PERFIL=large stress-solvers.js
k6 run -e MODULO=transporte -e SCENARIO=spike stress-solvers.js
```

| Variable | Valores | Default |
|---|---|---|
| `BASE_URL` | destino | `http://localhost:8080` |
| `SCENARIO` | `smoke` `load` `stress` `spike` `soak` | `stress` |
| `PERFIL` | `small` `medium` `large` | `medium` |
| `MODULO` | `lp` `transporte` `redes` `entera` `inventario` `dinamica` `todos` | `todos` |
| `SEED` | semilla del generador | `20260719` |

### Escenarios

| Escenario | Perfil de carga | Para qué |
|---|---|---|
| `smoke` | 1 VU, 1 iteración por endpoint | Verificar que los 24 payloads siguen siendo válidos |
| `load` | 10 VUs, ~3 min | Carga esperada en clase |
| `stress` | escalones 25 → 60 → 120 VUs, ~10 min | Encontrar el punto de quiebre |
| `spike` | 10 → 250 VUs de golpe | Ver si la JVM y el pool de hilos absorben la ráfaga |
| `soak` | 20 VUs, 30 min | Fugas de memoria y degradación del GC |

`PERFIL` escala el **tamaño de la instancia**, que es lo que realmente cuesta:
`large` son LP de 15×15, matrices de transporte 10×10, grafos de 60 nodos y 150 aristas.

## Cómo está construido

- **Round-robin sobre `iterationInTest`**, no azar: reparte la carga por igual entre los
  24 endpoints, sin el sesgo de muestreo que da elegir al azar.
- **Semilla distinta por iteración**: cada petición lleva una instancia diferente, así
  ningún caché abarata el resultado artificialmente.
- **Payloads factibles por construcción** — mide el costo del algoritmo, no el del
  validador rechazando basura. El grafo de redes lleva una espina dorsal `N0→…→Nk` que
  garantiza conectividad; la red por etapas conecta por completo etapas consecutivas;
  POQ genera `P` como múltiplo de `D` para cumplir `P > D`.
- `INFACTIBLE` y `NO_ACOTADO` **no cuentan como error** (son resultados válidos del
  contrato): van al contador aparte `solver_no_optimos`.
- **Thresholds por módulo**, porque el costo algorítmico no es comparable: inventarios
  son fórmulas cerradas (p95 < 300 ms), Branch & Bound es exponencial (p95 < 3 s).

`entera` se mantiene deliberadamente pequeño (≤7 variables): con 15, una sola petición
puede tocar el tope `MAX_NODOS=5000` y dominar toda la medición con un único outlier.

## Resultados de la validación (19-07-2026)

Ejecutado contra el backend por túnel SSH:

- `smoke` con `PERFIL=small` y `PERFIL=large`: **96/96 checks OK**, 0 fallos.
- `load`, 10 VUs, 3 min: **3197 peticiones, 0 fallos**, los seis thresholds en verde
  (p95 más alto: 168 ms en dinámica).

### Dos advertencias al leer los números

1. **El túnel SSH impone un piso de ~110 ms** a *toda* petición — incluido `eoq-basico`,
   que es una raíz cuadrada. Los tiempos absolutos de arriba son casi todo túnel. Para
   medir el backend de verdad hay que correr k6 en la misma red que la API.
2. **Un túnel SSH multiplexa sobre una sola conexión TCP.** Con `spike` (250 VUs) el
   cuello de botella será el túnel, no el servidor: los resultados dirán más del túnel
   que del backend. Los escenarios `stress` y `spike` solo son concluyentes ejecutados
   localmente o desde la misma red.

## Hallazgo: `POST /api/v1/redes/asignacion` devuelve 500 sin `dirigido`

El smoke test destapó un bug real. `ModeloRed.dirigido` es un `boolean` **primitivo**
(`ModeloRed.java:21`), así que omitirlo hace fallar a Jackson:

```json
{"error":"Error interno: JSON parse error: Cannot map `null` into type `boolean`"}
```

El problema es que **el cuerpo documentado en `docs/API_CONTRACT.md` (líneas 545-550) no
incluye `dirigido`** — el ejemplo del contrato, copiado tal cual, devuelve HTTP 500. Y
la asignación ni siquiera usa el grafo: construye su propia red bipartita.

Aquí se sorteó mandando `dirigido: true` en el payload. La corrección de fondo es del
backend, y hay tres caminos:

- cambiar el campo a `Boolean` (envuelto) — el más simple;
- registrar `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = false`;
- o dejar el código y corregir el ejemplo del contrato.

Vale la pena decidirlo aparte: hoy cualquier cliente que siga la documentación al pie de
la letra recibe un 500.
