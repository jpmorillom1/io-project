import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router'
import { enviarMensaje, decidirAprobacion } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { Mensaje, ChatResponse, SolicitudAprobacion, ModeloTransporte, ModeloRed, ModeloEntero, ModeloInventario } from '@/types/io'

const SESSION_KEY = 'io_sesion_id'

type Modulo = 'lp' | 'transporte' | 'redes' | 'pl-entera' | 'inventario'

interface ChatContextValue {
  mensajes: Mensaje[]
  enviar: (texto: string) => Promise<void>
  decidir: (aprobado: boolean, comentario?: string | null) => Promise<void>
  solicitud: SolicitudAprobacion | null
  isSending: boolean
  error: string | null
}

const ChatContext = createContext<ChatContextValue | null>(null)

/**
 * Deduce a qué módulo pertenece una respuesta del tutor, para que el workspace
 * se adapte automáticamente (LP ⇄ Transporte ⇄ Redes) según lo que el chat detecte.
 */
function moduloDeRespuesta(res: ChatResponse): Modulo | null {
  if (res.resultadoInventario) return 'inventario'
  if (res.solicitudAprobacion?.metodo === 'INVENTARIO') return 'inventario'
  if (res.resultadoEntero) return 'pl-entera'
  if (res.solicitudAprobacion?.metodo === 'BRANCH_AND_BOUND') return 'pl-entera'
  if (res.resultadoRed) return 'redes'
  if (res.solicitudAprobacion?.metodo === 'REDES') return 'redes'
  if (res.resultadoTransporte) return 'transporte'
  if (res.solicitudAprobacion?.metodo === 'TRANSPORTE') return 'transporte'
  if (res.resultado || res.resultadoGrafico || res.modeloSugerido) return 'lp'
  if (res.solicitudAprobacion) return 'lp' // métodos LP (SIMPLEX/GRAN_M/DOS_FASES/GRAFICO)
  return null
}

/**
 * Estado del chat compartido por toda la app. Vive por encima de las rutas
 * (montado en AppShell), de modo que cambiar de workspace NO reinicia la
 * conversación. El chat es el orquestador: actualiza el store y navega al
 * módulo correcto cuando el tutor toma decisiones.
 */
export function ChatProvider({ children }: { children: ReactNode }) {
  const [mensajes, setMensajes] = useState<Mensaje[]>([])
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [solicitud, setSolicitud] = useState<SolicitudAprobacion | null>(null)
  const sesionId = useWorkspaceStore(s => s.sesionId)
  const setSesionId = useWorkspaceStore(s => s.setSesionId)
  const store = useWorkspaceStore.getState
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const stored = sessionStorage.getItem(SESSION_KEY)
    if (stored) setSesionId(stored)
  }, [setSesionId])

  function irAModulo(modulo: Modulo | null) {
    if (!modulo) return
    if (!location.pathname.startsWith(`/${modulo}`)) {
      navigate(`/${modulo}`)
    }
  }

  function procesarRespuesta(res: ChatResponse) {
    if (res.modeloSugerido) {
      store().setModelo(res.modeloSugerido)
      store().setStatus('EDITING')
      store().setUltimaActualizacionIA('modelo')
    }
    if (res.validacion) {
      store().setValidacion(res.validacion)
      store().setStatus(res.validacion.esValido ? 'EDITING' : 'INVALID')
      store().setUltimaActualizacionIA('validacion')
    }
    if (res.resultado) {
      store().setResultado(res.resultado)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    if (res.resultadoGrafico) {
      store().setResultadoGrafico(res.resultadoGrafico)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    if (res.resultadoTransporte) {
      store().setResultadoTransporte(res.resultadoTransporte)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    if (res.resultadoRed) {
      store().setResultadoRed(res.resultadoRed)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    if (res.resultadoEntero) {
      store().setResultadoEntero(res.resultadoEntero)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    if (res.resultadoInventario) {
      store().setResultadoInventario(res.resultadoInventario)
      store().setStatus('SOLVED')
      store().setUltimaActualizacionIA('resultado')
    }
    // Al pedir aprobación de un transporte, refleja el modelo en el editor de matriz.
    if (res.solicitudAprobacion?.metodo === 'TRANSPORTE') {
      store().setModeloTransporte(res.solicitudAprobacion.modelo as ModeloTransporte)
      store().setStatus('EDITING')
    }
    // Ídem para redes: refleja el modelo en el editor de grafo.
    if (res.solicitudAprobacion?.metodo === 'REDES') {
      store().setModeloRed(res.solicitudAprobacion.modelo as ModeloRed)
      store().setStatus('EDITING')
    }
    // Ídem para PL Entera: refleja el modelo en el editor (relajación + tipos).
    if (res.solicitudAprobacion?.metodo === 'BRANCH_AND_BOUND') {
      store().setModeloEntero(res.solicitudAprobacion.modelo as ModeloEntero)
      store().setStatus('EDITING')
    }
    // Ídem para Inventarios: refleja el modelo en el editor de parámetros.
    if (res.solicitudAprobacion?.metodo === 'INVENTARIO') {
      store().setModeloInventario(res.solicitudAprobacion.modelo as ModeloInventario)
      store().setStatus('EDITING')
    }
    // Una nueva solicitud de la misma sesión reemplaza la anterior en el backend;
    // si el turno no trae ninguna, la pendiente ya no está en vuelo.
    setSolicitud(res.solicitudAprobacion)

    // Adapta el workspace al módulo que el tutor está trabajando.
    irAModulo(moduloDeRespuesta(res))

    const msgTutor: Mensaje = { rol: 'tutor', texto: res.respuesta, timestamp: Date.now() }
    setMensajes(prev => [...prev, msgTutor])
  }

  async function enviar(texto: string) {
    if (!texto.trim()) return
    const msgUser: Mensaje = { rol: 'user', texto, timestamp: Date.now() }
    setMensajes(prev => [...prev, msgUser])
    setIsSending(true)
    store().setIsChatBusy(true)
    setError(null)
    try {
      const res = await enviarMensaje(sesionId, texto)
      if (!sesionId) {
        setSesionId(res.sesionId)
        sessionStorage.setItem(SESSION_KEY, res.sesionId)
      }
      procesarRespuesta(res)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Error de conexión'
      setError(msg)
      setSesionId(null)
      sessionStorage.removeItem(SESSION_KEY)
    } finally {
      setIsSending(false)
      store().setIsChatBusy(false)
    }
  }

  async function decidir(aprobado: boolean, comentario: string | null = null) {
    if (!solicitud || isSending) return
    const solicitudId = solicitud.solicitudId
    setSolicitud(null)
    setIsSending(true)
    store().setIsChatBusy(true)
    setError(null)
    try {
      const res = await decidirAprobacion({ solicitudId, aprobado, comentario })
      procesarRespuesta(res)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Error de conexión'
      setError(msg)
    } finally {
      setIsSending(false)
      store().setIsChatBusy(false)
    }
  }

  const value: ChatContextValue = { mensajes, enviar, decidir, solicitud, isSending, error }
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

/** Consume el estado del chat compartido. Debe usarse dentro de <ChatProvider>. */
export function useChat(): ChatContextValue {
  const ctx = useContext(ChatContext)
  if (!ctx) throw new Error('useChat debe usarse dentro de <ChatProvider>')
  return ctx
}
