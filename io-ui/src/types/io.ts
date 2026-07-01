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
