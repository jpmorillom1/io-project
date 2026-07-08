export type TipoObjetivo = 'MAXIMIZAR' | 'MINIMIZAR'
export type TipoRestriccion = 'LEQ' | 'GEQ' | 'EQ'
export type SolveStatus = 'OPTIMO' | 'INFACTIBLE' | 'NO_ACOTADO' | 'MULTIPLE_OPTIMO' | 'ERROR'

export interface Restriccion {
  coeficientes: number[]
  tipo: TipoRestriccion
  rhs: number
}

export interface FuncionObjetivo {
  coeficientes: number[]
  tipo: TipoObjetivo
}

export interface ModeloLP {
  variables: string[]
  objetivo: FuncionObjetivo
  restricciones: Restriccion[]
}

export interface RangoCoeficiente {
  variable: string
  valorActual: number
  min: number | null   // null = -∞
  max: number | null   // null = +∞
}

export interface RangoRHS {
  restriccion: string
  valorActual: number
  min: number | null   // null = -∞
  max: number | null   // null = +∞
}

export interface RangosSensibilidad {
  coeficientesObjetivo: RangoCoeficiente[]
  rhs: RangoRHS[]
}

export interface SolucionLP {
  valores: Record<string, number>
  holguras: Record<string, number>
  valorOptimo: number
  preciosSombra: Record<string, number>
  rangosSensibilidad: RangosSensibilidad
}

export interface StepDatos {
  encabezados: string[]
  tableau: number[][]
  base: string[]
  varEntra?: string
  varSale?: string
  valorOptimo?: number
  status?: SolveStatus
}

export interface SolveStep {
  numero: number
  titulo: string
  descripcion: string
  datos: StepDatos
}

export interface SolveResult {
  status: SolveStatus
  solution: SolucionLP | null
  steps: SolveStep[]
}

export interface ChatRequest {
  sesionId: string | null
  mensaje: string
}

export interface ChatResponse {
  sesionId: string
  respuesta: string
  modeloSugerido: ModeloLP | null
  validacion: ValidacionResponse | null
  resultado: SolveResult | null
  resultadoGrafico: SolveResultGrafico | null
  resultadoTransporte: SolveResultTransporte | null
  resultadoRed: SolveResultRed | null
  resultadoEntero: SolveResultEntera | null
  solicitudAprobacion: SolicitudAprobacion | null
}

// ── Human-in-the-Loop ────────────────────────────────────────────────────────
// El tutor quiere resolver y espera la aprobación del estudiante.
// El solver NO corre hasta enviar la decisión a POST /ai/chat/aprobacion.

export type MetodoResolucion =
  | 'SIMPLEX' | 'GRAN_M' | 'DOS_FASES' | 'GRAFICO'
  | 'TRANSPORTE' | 'REDES' | 'BRANCH_AND_BOUND'

export interface SolicitudAprobacion {
  solicitudId: string
  metodo: MetodoResolucion
  // El modelo es genérico: ModeloLP para métodos LP/gráfico, ModeloTransporte
  // para TRANSPORTE, ModeloRed para REDES, ModeloEntero para BRANCH_AND_BOUND.
  modelo: ModeloLP | ModeloTransporte | ModeloRed | ModeloEntero
}

export interface DecisionAprobacionRequest {
  solicitudId: string
  aprobado: boolean
  comentario: string | null   // en un rechazo, explica qué corregir (re-alimenta al tutor)
}

export interface ModeloSugeridoResponse {
  modelo: ModeloLP | null
  razonamiento: string
  supuestosAplicados: string[]
  advertencias: string[]
}

export interface ValidarModeloRequest {
  descripcionProblema: string
  modelo: ModeloLP
}

export interface ValidacionResponse {
  esValido: boolean
  analisis: string
  erroresEncontrados: string[]
  sugerencias: string[]
  modeloCorregido: ModeloLP | null
}

export interface Mensaje {
  rol: 'user' | 'tutor'
  texto: string
  timestamp: number
}

export interface TableauHighlights {
  columnaEntrada: number | null
  filaSalida: number | null
  celdaPivote: [number, number] | null
}

export type WorkspaceStatus =
  | 'IDLE'
  | 'SUGGESTING'
  | 'EDITING'
  | 'VALIDATING'
  | 'INVALID'
  | 'SOLVING'
  | 'SOLVED'

// ── Método gráfico ─────────────────────────────────────────────────────────

export interface PuntoVertice {
  x: number
  y: number
  valorZ: number
  esOptimo: boolean
  etiqueta: string
}

export interface LineaGrafico {
  indice: number
  etiqueta: string
  tipo: TipoRestriccion
  puntos: Array<{ x: number; y: number }>
}

export interface StepDatosGrafico {
  tipo: 'GRAFICO'
  var1: string
  var2: string
  xMax: number
  yMax: number
  lineas: LineaGrafico[]
  vertices: PuntoVertice[]
  region: Array<[number, number]>
}

export interface SolveStepGrafico {
  numero: number
  titulo: string
  descripcion: string
  datos: StepDatosGrafico
}

export interface SolucionGrafica {
  valores: Record<string, number>
  valorOptimo: number
  vertices: PuntoVertice[]
  region: Array<[number, number]>
  xMax: number
  yMax: number
}

export interface SolveResultGrafico {
  status: SolveStatus
  solution: SolucionGrafica | null
  steps: SolveStepGrafico[]
}

// ── Transporte ──────────────────────────────────────────────────────────────

export type MetodoTransporte = 'ESQUINA_NOROESTE' | 'COSTO_MINIMO' | 'VOGEL' | 'MODI'

export interface ModeloTransporte {
  origenes: string[]
  destinos: string[]
  oferta: number[]
  demanda: number[]
  costos: number[][]        // filas = orígenes, columnas = destinos
  metodo: MetodoTransporte
}

export interface CostoPorMetodo {
  metodo: MetodoTransporte
  costoInicial: number
}

export interface SolucionTransporte {
  origenes: string[]        // incluye "Ficticio" si hubo balanceo
  destinos: string[]
  asignaciones: number[][]  // cantidad enviada por ruta (0 si no se usa)
  costoTotal: number
  comparativaInicial: CostoPorMetodo[] | null   // solo MODI
  metodoInicial: MetodoTransporte | null        // solo MODI
}

// Cada paso de transporte lleva la tabla y datos específicos del método.
// `asignaciones` usa null en celdas no básicas (rutas sin uso) durante los pasos.
export interface StepDatosTransporte {
  tipo: 'TRANSPORTE'
  origenes: string[]
  destinos: string[]
  costos: number[][]
  oferta: number[]
  demanda: number[]
  asignaciones: (number | null)[][]
  celda?: [number, number]
  cantidad?: number
  costoTotal?: number
  celdasBasicas?: number
  // Vogel
  penalizacionesFila?: (number | null)[]
  penalizacionesColumna?: (number | null)[]
  lineaElegida?: string
  // MODI
  u?: number[]
  v?: number[]
  costosReducidos?: (number | null)[][]
  celdaEntrante?: [number, number]
  celdaSaliente?: [number, number]
  ciclo?: [number, number][]
  theta?: number
  comparativaInicial?: Array<{ metodo: string; costoInicial: number }>
  metodoInicial?: string
  status?: SolveStatus
}

export interface SolveStepTransporte {
  numero: number
  titulo: string
  descripcion: string
  datos: StepDatosTransporte
}

export interface SolveResultTransporte {
  status: SolveStatus
  solution: SolucionTransporte | null
  steps: SolveStepTransporte[]
}

// ── Redes ───────────────────────────────────────────────────────────────────

export type MetodoRed =
  | 'DIJKSTRA'
  | 'KRUSKAL'
  | 'EDMONDS_KARP'
  | 'FLUJO_COSTO_MINIMO'
  | 'ASIGNACION'

// Campos nullable según el método: peso → DIJKSTRA/KRUSKAL ·
// capacidad → EDMONDS_KARP/FLUJO_COSTO_MINIMO · costo → FLUJO_COSTO_MINIMO
export interface Arista {
  origen: string
  destino: string
  peso?: number | null
  capacidad?: number | null
  costo?: number | null
}

export interface ModeloRed {
  nodos: string[] | null
  aristas: Arista[] | null
  dirigido: boolean | null
  metodo: MetodoRed
  fuente: string | null
  sumidero: string | null
  // Solo ASIGNACION (los campos de grafo van null)
  agentes: string[] | null
  tareas: string[] | null
  matrizCostos: number[][] | null
}

export interface SolucionRed {
  distancias: Record<string, number> | null   // DIJKSTRA
  rutaOptima: string[] | null                 // DIJKSTRA (si hubo sumidero)
  aristasSolucion: Arista[] | null            // ruta/árbol/arcos con flujo/pares
  flujoPorArco: Record<string, number> | null // clave "origen->destino"
  asignacion: Record<string, string> | null   // agente → tarea (sin ficticios)
  valorObjetivo: number | null
  flujoTotal: number | null
  costoTotal: number | null
}

export type EstadoArista = 'normal' | 'activa' | 'solucion' | 'descartada'

// Arista tal como llega en steps[].datos: con su estado en ESE paso.
export interface AristaPaso extends Arista {
  estado?: EstadoArista
  flujo?: number | null
}

export interface StepDatosRed {
  tipo: 'REDES'
  metodo: MetodoRed
  nodos: string[]
  aristas: AristaPaso[]
  fuente?: string | null
  sumidero?: string | null
  // Dijkstra
  nodoActual?: string
  asentados?: string[]
  distancias?: Record<string, number>
  rutaOptima?: string[]
  distancia?: number
  // Kruskal
  aristaEvaluada?: string
  pesoAcumulado?: number
  pesoTotal?: number
  // Edmonds-Karp / Flujo de costo mínimo
  camino?: string[]
  cuelloBotella?: number
  flujoTotal?: number
  costoUnitario?: number
  costoAcumulado?: number
  costoTotal?: number
  // Asignación
  agentes?: string[]
  tareas?: string[]
  asignacion?: Record<string, string>
  status?: SolveStatus
}

export interface SolveStepRed {
  numero: number
  titulo: string
  descripcion: string
  datos: StepDatosRed
}

export interface SolveResultRed {
  status: SolveStatus
  solution: SolucionRed | null
  steps: SolveStepRed[]
}

// ── PL Entera (Branch & Bound) ────────────────────────────────────────────────

export type TipoVariable = 'ENTERA' | 'BINARIA' | 'CONTINUA'

export interface ModeloEntero {
  relajacion: ModeloLP            // variables/objetivo/restricciones de la relajación
  tiposVariable: TipoVariable[]   // alineado por índice con relajacion.variables
}

export interface SolucionEntera {
  valores: Record<string, number> // solución entera óptima
  valorOptimo: number             // Z* entero
  valorRelajacion: number         // óptimo LP de la raíz (para la brecha de integralidad)
  nodosExplorados: number
}

export type AccionNodo = 'RAMIFICA' | 'INCUMBENTE' | 'PODA_COTA' | 'PODA_INFACTIBLE'

// datos de un paso de Branch & Bound (nodo del árbol o paso final)
export interface EnteraStepDatos {
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

export interface SolveStepEntera {
  numero: number
  titulo: string
  descripcion: string
  datos: EnteraStepDatos
}

export interface SolveResultEntera {
  status: SolveStatus
  solution: SolucionEntera | null
  steps: SolveStepEntera[]
}
