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
  resultadoInventario: SolveResultInventario | null
  resultadoDinamica: SolveResultDinamica | null
  solicitudAprobacion: SolicitudAprobacion | null
}

// ── Human-in-the-Loop ────────────────────────────────────────────────────────
// El tutor quiere resolver y espera la aprobación del estudiante.
// El solver NO corre hasta enviar la decisión a POST /ai/chat/aprobacion.

export type MetodoResolucion =
  | 'SIMPLEX' | 'GRAN_M' | 'DOS_FASES' | 'GRAFICO'
  | 'TRANSPORTE' | 'REDES' | 'BRANCH_AND_BOUND' | 'INVENTARIO'
  | 'PROGRAMACION_DINAMICA'

export interface SolicitudAprobacion {
  solicitudId: string
  metodo: MetodoResolucion
  // El modelo es genérico: ModeloLP para métodos LP/gráfico, ModeloTransporte
  // para TRANSPORTE, ModeloRed para REDES, ModeloEntero para BRANCH_AND_BOUND,
  // ModeloInventario para INVENTARIO, ModeloDinamico para PROGRAMACION_DINAMICA
  // (el submodelo concreto viaja en modelo.metodo).
  modelo: ModeloLP | ModeloTransporte | ModeloRed | ModeloEntero | ModeloInventario | ModeloDinamico
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

// ── Actividad en vivo ────────────────────────────────────────────────────────

/** Fases de un turno del chat. Es el enum FaseActividad del backend. */
export type FaseActividad =
  | 'PENSANDO'
  | 'ENRUTANDO'
  | 'FORMULANDO'
  | 'VALIDANDO'
  | 'PREPARANDO'
  | 'RESOLVIENDO'
  | 'EXPLICANDO'

/**
 * Lo que Pivot está haciendo ahora mismo. El `texto` viene ya compuesto desde el
 * backend ("Resolviendo con MODI") — aquí NO se traduce nada: el vocabulario vive
 * en un solo sitio, en `FaseActividad.java`.
 */
export interface Actividad {
  fase: FaseActividad
  texto: string
  /** Contador monótono: sirve para descartar sondeos que lleguen fuera de orden. */
  secuencia: number
}

// ── Historial de conversaciones ──────────────────────────────────────────────

/** Módulo que atiende una sesión. Es el nombre del enum ModuloIO del backend. */
export type ModuloActivo = 'PL' | 'INVENTARIO' | 'TRANSPORTE' | 'REDES' | 'ENTERA' | 'DINAMICA'

/** Mensaje del transcript persistido. */
export interface MensajeHistorial {
  rol: 'user' | 'tutor'
  texto: string
  fecha: string
}

/** Una conversación en la barra lateral. GET /ai/sesiones las devuelve por actividad reciente. */
export interface ResumenSesion {
  sesionId: string
  titulo: string
  moduloActivo: ModuloActivo | null
  actualizada: string
}

/**
 * Último problema que la sesión resolvió. `metodo` decide a qué editor va `modelo`
 * y a qué panel va `resultado` — por eso ambos llegan sin tipar.
 */
export interface ProblemaResueltoHistorial {
  metodo: MetodoResolucion
  modelo: unknown
  resultado: unknown
}

/** Lo que devuelve GET /ai/chat/{sesionId}/historial: chat + workspace de una conversación. */
export interface HistorialSesion {
  sesionId: string
  titulo: string
  moduloActivo: ModuloActivo | null
  mensajes: MensajeHistorial[]
  ultimoProblema: ProblemaResueltoHistorial | null
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

// ── Inventarios (modelos deterministas) ───────────────────────────────────────

export type MetodoInventario =
  | 'EOQ_BASICO' | 'EOQ_DESCUENTOS' | 'EOQ_FALTANTES'
  | 'PRODUCCION_ECONOMICA' | 'PUNTO_REORDEN'

export interface TramoDescuento {
  cantidadMinima: number
  precioUnitario: number
}

// Body de los endpoints /api/v1/inventario/*. Solo se envían los campos que
// aplican al submodelo; el endpoint fuerza el metodo, así que es opcional.
export interface ModeloInventario {
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
export interface ComparativaTramo {
  precioUnitario: number
  cantidad: number
  costoTotal: number
  factible: boolean
}

// Record unificado: solo los campos del submodelo resuelto vienen non-null.
export interface SolucionInventario {
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
export interface InventarioStepDatos {
  tipo: 'INVENTARIO'
  metodo: MetodoInventario
  formula?: string       // fórmula simbólica
  sustitucion?: string   // fórmula con números sustituidos
  resultado?: number     // valor computado en el paso
}

export interface SolveStepInventario {
  numero: number
  titulo: string
  descripcion: string
  datos: InventarioStepDatos
}

export interface SolveResultInventario {
  status: SolveStatus
  solution: SolucionInventario | null
  steps: SolveStepInventario[]
}

// ── Programación Dinámica (determinística) ────────────────────────────────────

export type MetodoDinamico =
  | 'ASIGNACION_RECURSOS' | 'MOCHILA' | 'RUTA_ETAPAS'
  | 'PLANIFICACION_PRODUCCION' | 'REEMPLAZO_EQUIPOS'

export type SentidoOptimizacion = 'MAXIMIZAR' | 'MINIMIZAR'

export interface ActividadRecurso { nombre: string; retornos: number[] } // longitud = recursoTotal + 1
export interface ArticuloMochila { nombre: string; peso: number; valor: number; unidadesMaximas?: number }
export interface EtapaRuta { etapa: number; nodos: string[] }
export interface ArcoRuta { origen: string; destino: string; costo: number }
export interface DatosEdadEquipo { edad: number; ingreso: number; costoOperacion: number; valorRescate: number }

// Body de los endpoints /api/v1/dinamica/*. Solo se envían los campos del submodelo;
// el endpoint fuerza el metodo, así que es opcional.
export interface ModeloDinamico {
  metodo?: MetodoDinamico
  sentido?: SentidoOptimizacion   // SOLO configurable en asignación de recursos y ruta por etapas

  // ASIGNACION_RECURSOS
  recursoTotal?: number
  actividades?: ActividadRecurso[]

  // MOCHILA
  capacidad?: number
  articulos?: ArticuloMochila[]

  // RUTA_ETAPAS
  etapasRuta?: EtapaRuta[]
  arcos?: ArcoRuta[]

  // PLANIFICACION_PRODUCCION
  demandas?: number[]
  costoPreparacion?: number
  costoUnitarioProduccion?: number
  costoMantener?: number
  capacidadProduccion?: number
  capacidadAlmacen?: number
  inventarioInicial?: number
  inventarioFinal?: number

  // REEMPLAZO_EQUIPOS
  horizonteAnios?: number
  edadInicial?: number
  edadMaxima?: number
  costoCompra?: number
  tablaEdades?: DatosEdadEquipo[]
}

// Una celda de la tabla: valorTotal = contribucion + valorFuturo (muestra las tres columnas).
export interface EvaluacionDecision {
  decision: string        // ya formateado: "x = 2", "CONSERVAR", "ir a C"
  contribucion: number    // retorno/costo inmediato de la decisión
  valorFuturo: number     // f_(k+1) del estado al que lleva
  valorTotal: number
  optima: boolean         // la que gana la fila
}

export interface FilaEtapa {
  estado: string          // ya formateado: "s = 4", "edad = 2", "B"
  evaluaciones: EvaluacionDecision[]
  decisionOptima: string
  valorOptimo: number     // f_k(estado)
}

export interface TablaEtapa {
  etapa: number
  nombreEtapa: string
  recurrencia: string     // la recurrencia instanciada para esta etapa
  filas: FilaEtapa[]      // solo estados ALCANZABLES — no asumas un rango contiguo
}

export interface DecisionOptima {
  etapa: number
  nombreEtapa: string
  estadoEntrada: string
  decision: string
  contribucion: number
  estadoSalida: string
}

// Record unificado: solo rutaOptima depende del submodelo (null salvo en RUTA_ETAPAS).
export interface SolucionDinamica {
  valorOptimo: number
  tablas: TablaEtapa[]            // ORDEN HACIA ATRÁS: tablas[0] es la ÚLTIMA etapa
  politicaOptima: DecisionOptima[] // orden hacia adelante: etapa 1 → n
  rutaOptima: string[] | null     // solo RUTA_ETAPAS
  definicionEtapas: string
  definicionEstados: string
  definicionDecisiones: string
  funcionRecurrencia: string
  principioOptimalidad: string
  interpretacionPolitica: string
}

// datos de un paso de PD. Discrimina por qué clave trae:
//   `etapas`  → paso de formulación (siempre el nº 1)
//   `tabla`   → paso de etapa
//   `politica`→ paso de recuperación (siempre el último)
export interface DinamicaStepDatos {
  tipo: 'PROGRAMACION_DINAMICA'
  metodo: MetodoDinamico
  // paso de formulación
  etapas?: string
  estados?: string
  decisiones?: string
  recurrencia?: string
  principioOptimalidad?: string
  // paso de etapa
  tabla?: TablaEtapa
  // paso de recuperación
  politica?: DecisionOptima[]
  valorOptimo?: number
  rutaOptima?: string[]
}

export interface SolveStepDinamica {
  numero: number
  titulo: string
  descripcion: string
  datos: DinamicaStepDatos
}

export interface SolveResultDinamica {
  status: SolveStatus
  solution: SolucionDinamica | null // null si INFACTIBLE (ruta sin camino / producción sin capacidad)
  steps: SolveStepDinamica[]        // vienen igual con INFACTIBLE: explican por qué
}
