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
  solicitudAprobacion: SolicitudAprobacion | null
}

// ── Human-in-the-Loop ────────────────────────────────────────────────────────
// El tutor quiere resolver y espera la aprobación del estudiante.
// El solver NO corre hasta enviar la decisión a POST /ai/chat/aprobacion.

export type MetodoResolucion = 'SIMPLEX' | 'GRAN_M' | 'DOS_FASES' | 'GRAFICO' | 'TRANSPORTE'

export interface SolicitudAprobacion {
  solicitudId: string
  metodo: MetodoResolucion
  // El modelo es genérico: ModeloLP para métodos LP/gráfico, ModeloTransporte para TRANSPORTE.
  modelo: ModeloLP | ModeloTransporte
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
