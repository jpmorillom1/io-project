import type {
  ModeloLP, SolveResult, SolveResultGrafico, ChatResponse, ModeloSugeridoResponse, ValidacionResponse,
  DecisionAprobacionRequest, ModeloTransporte, SolveResultTransporte, MetodoTransporte,
  ModeloRed, SolveResultRed, MetodoRed,
  ModeloEntero, SolveResultEntera,
  ModeloInventario, SolveResultInventario, MetodoInventario,
  ModeloDinamico, SolveResultDinamica, MetodoDinamico,
  Mensaje, MensajeHistorial, ResumenSesion, HistorialSesion,
} from '@/types/io'

const API_BASE = 'http://localhost:8080/api/v1'

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'Error de red' }))
    throw new Error(err.error ?? 'Error desconocido')
  }
  return res.json()
}

export async function resolverSimplex(modelo: ModeloLP): Promise<SolveResult> {
  const res = await fetch(`${API_BASE}/lp/simplex`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

export async function resolverGranM(modelo: ModeloLP): Promise<SolveResult> {
  const res = await fetch(`${API_BASE}/lp/gran-m`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

export async function resolverDosFases(modelo: ModeloLP): Promise<SolveResult> {
  const res = await fetch(`${API_BASE}/lp/dos-fases`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

export async function resolverGrafico(modelo: ModeloLP): Promise<SolveResultGrafico> {
  const res = await fetch(`${API_BASE}/lp/grafico`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

const RUTA_TRANSPORTE: Record<MetodoTransporte, string> = {
  ESQUINA_NOROESTE: 'esquina-noroeste',
  COSTO_MINIMO: 'costo-minimo',
  VOGEL: 'vogel',
  MODI: 'modi',
}

export async function resolverTransporte(modelo: ModeloTransporte): Promise<SolveResultTransporte> {
  const res = await fetch(`${API_BASE}/transporte/${RUTA_TRANSPORTE[modelo.metodo]}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

const RUTA_RED: Record<MetodoRed, string> = {
  DIJKSTRA: 'dijkstra',
  KRUSKAL: 'kruskal',
  EDMONDS_KARP: 'edmonds-karp',
  FLUJO_COSTO_MINIMO: 'flujo-costo-minimo',
  ASIGNACION: 'asignacion',
}

export async function resolverRed(modelo: ModeloRed): Promise<SolveResultRed> {
  const res = await fetch(`${API_BASE}/redes/${RUTA_RED[modelo.metodo]}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

export async function resolverBranchAndBound(modelo: ModeloEntero): Promise<SolveResultEntera> {
  const res = await fetch(`${API_BASE}/entera/branch-and-bound`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

const RUTA_INVENTARIO: Record<MetodoInventario, string> = {
  EOQ_BASICO: 'eoq-basico',
  EOQ_DESCUENTOS: 'eoq-descuentos',
  EOQ_FALTANTES: 'eoq-faltantes',
  PRODUCCION_ECONOMICA: 'produccion-economica',
  PUNTO_REORDEN: 'punto-reorden',
}

export async function resolverInventario(
  metodo: MetodoInventario,
  modelo: ModeloInventario
): Promise<SolveResultInventario> {
  const res = await fetch(`${API_BASE}/inventario/${RUTA_INVENTARIO[metodo]}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

const RUTA_DINAMICA: Record<MetodoDinamico, string> = {
  ASIGNACION_RECURSOS: 'asignacion-recursos',
  MOCHILA: 'mochila',
  RUTA_ETAPAS: 'ruta-etapas',
  PLANIFICACION_PRODUCCION: 'planificacion-produccion',
  REEMPLAZO_EQUIPOS: 'reemplazo-equipos',
}

export async function resolverDinamica(
  metodo: MetodoDinamico,
  modelo: ModeloDinamico
): Promise<SolveResultDinamica> {
  const res = await fetch(`${API_BASE}/dinamica/${RUTA_DINAMICA[metodo]}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelo),
  })
  return handleResponse(res)
}

export async function enviarMensaje(
  sesionId: string | null,
  mensaje: string
): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE}/ai/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sesionId, mensaje }),
  })
  return handleResponse(res)
}

// Conversaciones para la barra lateral, la más reciente primero.
export async function listarSesiones(): Promise<ResumenSesion[]> {
  const res = await fetch(`${API_BASE}/ai/sesiones`)
  return handleResponse(res)
}

// Transcript + último resultado de una sesión. Devuelve null si el backend ya no la conoce
// (404): el sesionId guardado está rancio y hay que empezar una sesión nueva.
export async function obtenerHistorial(sesionId: string): Promise<HistorialSesion | null> {
  const res = await fetch(`${API_BASE}/ai/chat/${sesionId}/historial`)
  if (res.status === 404) return null
  return handleResponse(res)
}

// El transcript llega con fechas ISO; el chat pinta timestamps.
export function aMensajes(historial: MensajeHistorial[]): Mensaje[] {
  return historial.map(m => ({
    rol: m.rol,
    texto: m.texto,
    timestamp: new Date(m.fecha).getTime(),
  }))
}

// Decisión HITL sobre una solicitud de resolución pendiente.
// Devuelve un ChatResponse: si aprobó, con resultado/resultadoGrafico + explicación
// del tutor; si rechazó, normalmente con un modeloSugerido corregido.
export async function decidirAprobacion(
  decision: DecisionAprobacionRequest
): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE}/ai/chat/aprobacion`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(decision),
  })
  return handleResponse(res)
}

export async function sugerirModelo(descripcion: string): Promise<ModeloSugeridoResponse> {
  const res = await fetch(`${API_BASE}/ai/sugerir-modelo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ descripcionProblema: descripcion }),
  })
  return handleResponse(res)
}

export async function validarModelo(
  descripcion: string,
  modelo: ModeloLP
): Promise<ValidacionResponse> {
  const res = await fetch(`${API_BASE}/ai/validar-modelo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ descripcionProblema: descripcion, modelo }),
  })
  return handleResponse(res)
}
