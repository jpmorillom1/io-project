/**
 * Instancias de prueba con ÓPTIMO CONOCIDO — el oráculo de las aserciones.
 *
 * Los valores esperados están verificados contra los tests JUnit del backend y los
 * ejemplos de `docs/API_CONTRACT.md`. NO cambiar sin recalcular el óptimo.
 *
 * Estas estructuras son deliberadamente independientes de los tipos de `io-ui/src`
 * para que el paquete e2e no dependa del alias `@/` ni del build del frontend.
 */

// ─────────────────────────── Programación Lineal ───────────────────────────

/** MAX 5x1+4x2 s.a 6x1+4x2≤24, x1+2x2≤6 → Z=21 en (3, 1.5). */
export const LP_SIMPLEX = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [5.0, 4.0], tipo: 'MAXIMIZAR' },
  restricciones: [
    { coeficientes: [6.0, 4.0], tipo: 'LEQ', rhs: 24.0 },
    { coeficientes: [1.0, 2.0], tipo: 'LEQ', rhs: 6.0 },
  ],
}
export const LP_SIMPLEX_OPTIMO = 21

/** Mismo objetivo con x1≥4 → óptimo (4,0) Z=20 (Gran M respeta la GEQ). */
export const LP_GRAN_M = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [5.0, 4.0], tipo: 'MAXIMIZAR' },
  restricciones: [
    { coeficientes: [6.0, 4.0], tipo: 'LEQ', rhs: 24.0 },
    { coeficientes: [1.0, 2.0], tipo: 'LEQ', rhs: 6.0 },
    { coeficientes: [1.0, 0.0], tipo: 'GEQ', rhs: 4.0 },
  ],
}
export const LP_GRAN_M_OPTIMO = 20

/** Restricciones contradictorias (x1≤2 y x1≥5) → INFACTIBLE. */
export const LP_INFACTIBLE = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [1.0, 1.0], tipo: 'MAXIMIZAR' },
  restricciones: [
    { coeficientes: [1.0, 0.0], tipo: 'LEQ', rhs: 2.0 },
    { coeficientes: [1.0, 0.0], tipo: 'GEQ', rhs: 5.0 },
  ],
}

/** MAX x1+x2 sin cota superior → NO_ACOTADO. */
export const LP_NO_ACOTADO = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [1.0, 1.0], tipo: 'MAXIMIZAR' },
  restricciones: [{ coeficientes: [1.0, -1.0], tipo: 'LEQ', rhs: 1.0 }],
}

/** Malformado: 3 coeficientes en el objetivo para 2 variables. */
export const LP_MALFORMADO = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [5.0, 4.0, 3.0], tipo: 'MAXIMIZAR' },
  restricciones: [{ coeficientes: [6.0, 4.0], tipo: 'LEQ', rhs: 24.0 }],
}

// ─────────────────────────────── Transporte ───────────────────────────────

/** Instancia clásica (Taha). Óptimo MODI = 240. */
export const TRANSPORTE = {
  origenes: ['O1', 'O2', 'O3'],
  destinos: ['D1', 'D2', 'D3'],
  oferta: [20, 30, 25],
  demanda: [30, 25, 20],
  costos: [
    [4, 6, 8],
    [6, 4, 2],
    [2, 8, 6],
  ],
}
export const TRANSPORTE_OPTIMO = 240

/** Desbalanceado: Σoferta (75) ≠ Σdemanda (60) → el backend balancea y resuelve. */
export const TRANSPORTE_DESBALANCEADO = {
  origenes: ['O1', 'O2', 'O3'],
  destinos: ['D1', 'D2'],
  oferta: [20, 30, 25],
  demanda: [30, 30],
  costos: [
    [4, 6],
    [6, 4],
    [2, 8],
  ],
}

/** Malformado: la matriz de costos no cuadra con orígenes/destinos. */
export const TRANSPORTE_MALFORMADO = {
  origenes: ['O1', 'O2', 'O3'],
  destinos: ['D1', 'D2', 'D3'],
  oferta: [20, 30, 25],
  demanda: [30, 25, 20],
  costos: [[4, 6]],
}

// ───────────────────────────────── Redes ──────────────────────────────────

/** Grafo dirigido A→E. Ruta más corta conocida = 10 por A-C-B-D-E. */
export const RED_DIJKSTRA = {
  nodos: ['A', 'B', 'C', 'D', 'E'],
  aristas: [
    { origen: 'A', destino: 'B', peso: 4 },
    { origen: 'A', destino: 'C', peso: 2 },
    { origen: 'C', destino: 'B', peso: 1 },
    { origen: 'B', destino: 'D', peso: 5 },
    { origen: 'D', destino: 'E', peso: 2 },
  ],
  dirigido: true,
  fuente: 'A',
  sumidero: 'E',
}
export const RED_DIJKSTRA_OPTIMO = 10

/** MST por Kruskal (no dirigido). */
export const RED_KRUSKAL = {
  nodos: ['A', 'B', 'C', 'D'],
  aristas: [
    { origen: 'A', destino: 'B', peso: 1 },
    { origen: 'B', destino: 'C', peso: 2 },
    { origen: 'C', destino: 'D', peso: 3 },
    { origen: 'A', destino: 'C', peso: 4 },
  ],
  dirigido: false,
}
export const RED_KRUSKAL_OPTIMO = 6

/** Flujo máximo A→D. */
export const RED_EDMONDS_KARP = {
  nodos: ['A', 'B', 'C', 'D'],
  aristas: [
    { origen: 'A', destino: 'B', capacidad: 3 },
    { origen: 'A', destino: 'C', capacidad: 2 },
    { origen: 'B', destino: 'C', capacidad: 2 },
    { origen: 'B', destino: 'D', capacidad: 2 },
    { origen: 'C', destino: 'D', capacidad: 3 },
  ],
  dirigido: true,
  fuente: 'A',
  sumidero: 'D',
}
export const RED_EDMONDS_KARP_OPTIMO = 5

/** Asignación agentes→tareas. Óptimo conocido = 9 (A1→T2, A2→T3, A3→... etc.). */
export const RED_ASIGNACION = {
  agentes: ['A1', 'A2', 'A3'],
  tareas: ['T1', 'T2', 'T3'],
  matrizCostos: [
    [9, 2, 7],
    [6, 4, 3],
    [5, 8, 1],
  ],
}
export const RED_ASIGNACION_OPTIMO = 9

/** Grafo desconexo: E aislado, no alcanzable desde A → INFACTIBLE. */
export const RED_DESCONEXA = {
  nodos: ['A', 'B', 'E'],
  aristas: [{ origen: 'A', destino: 'B', peso: 3 }],
  dirigido: true,
  fuente: 'A',
  sumidero: 'E',
}

/** Peso negativo → IllegalArgumentException → HTTP 400. */
export const RED_PESO_NEGATIVO = {
  nodos: ['A', 'B'],
  aristas: [{ origen: 'A', destino: 'B', peso: -3 }],
  dirigido: true,
  fuente: 'A',
  sumidero: 'B',
}

// ───────────────────────────────── PL Entera ──────────────────────────────

/** Relajación LP (3,1.5)=21; óptimo entero (4,0)=20. */
export const ENTERA = {
  relajacion: {
    variables: ['x1', 'x2'],
    objetivo: { coeficientes: [5.0, 4.0], tipo: 'MAXIMIZAR' },
    restricciones: [
      { coeficientes: [6.0, 4.0], tipo: 'LEQ', rhs: 24.0 },
      { coeficientes: [1.0, 2.0], tipo: 'LEQ', rhs: 6.0 },
    ],
  },
  tiposVariable: ['ENTERA', 'ENTERA'],
}
export const ENTERA_OPTIMO = 20
export const ENTERA_RELAJACION = 21

// ──────────────────────────── Programación Dinámica ───────────────────────

/** Mochila 0/1, capacidad 5 → valor óptimo 7 (carga A+B: peso 5, valor 7). */
export const PD_MOCHILA = {
  capacidad: 5,
  articulos: [
    { nombre: 'A', peso: 2, valor: 3 },
    { nombre: 'B', peso: 3, valor: 4 },
    { nombre: 'C', peso: 4, valor: 5 },
  ],
}
export const PD_MOCHILA_OPTIMO = 7

/** Ruta por etapas A→D. Óptimo = 7 por A-C-D (4+3). */
export const PD_RUTA = {
  etapasRuta: [
    { etapa: 1, nodos: ['A'] },
    { etapa: 2, nodos: ['B', 'C'] },
    { etapa: 3, nodos: ['D'] },
  ],
  arcos: [
    { origen: 'A', destino: 'B', costo: 2 },
    { origen: 'A', destino: 'C', costo: 4 },
    { origen: 'B', destino: 'D', costo: 7 },
    { origen: 'C', destino: 'D', costo: 3 },
  ],
}
export const PD_RUTA_OPTIMO = 7

/** Ruta sin camino al destino D (B y C no conectan con D) → INFACTIBLE. */
export const PD_RUTA_SIN_CAMINO = {
  etapasRuta: [
    { etapa: 1, nodos: ['A'] },
    { etapa: 2, nodos: ['B', 'C'] },
    { etapa: 3, nodos: ['D'] },
  ],
  arcos: [
    { origen: 'A', destino: 'B', costo: 2 },
    { origen: 'A', destino: 'C', costo: 4 },
  ],
}

// ───────────────────────────────── Inventarios ────────────────────────────

/** EOQ básico: D=1000, K=50, H=4 → Q*=√(2·1000·50/4)=√25000≈158.11. */
export const INV_EOQ = {
  demanda: 1000,
  costoOrden: 50,
  costoMantener: 4,
}
export const INV_EOQ_QOPTIMO = 158.11

/** POQ inválido: P ≤ D (P=800 < D=1000) → HTTP 400. */
export const INV_POQ_INVALIDO = {
  demanda: 1000,
  costoOrden: 50,
  costoMantener: 4,
  tasaProduccion: 800,
}

// ─────────────────────────────── Chat / IA ────────────────────────────────

/** Enunciado con intento de inyección SQL — debe tratarse como texto plano. */
export const ENUNCIADO_SQLI =
  "Maximizar 3x1+2x2 sujeto a x1+x2<=4'; DROP TABLE sesion; --"

/** Enunciado con intento de XSS — el frontend debe escaparlo. */
export const ENUNCIADO_XSS =
  'Problema de LP <script>window.__xss=1</script> con dos variables'
