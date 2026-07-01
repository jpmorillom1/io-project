import type {
  ModeloLP, SolveResult, SolveResultGrafico, ChatResponse, ModeloSugeridoResponse, ValidacionResponse
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

export async function resolverGrafico(modelo: ModeloLP): Promise<SolveResultGrafico> {
  const res = await fetch(`${API_BASE}/lp/grafico`, {
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
