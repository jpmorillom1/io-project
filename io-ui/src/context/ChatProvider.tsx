import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router'
import {
  enviarMensaje, decidirAprobacion, obtenerHistorial, listarSesiones, aMensajes, obtenerActividad,
} from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type {
  Actividad,
  Mensaje, ChatResponse, SolicitudAprobacion, ModeloLP, SolveResult, SolveResultGrafico,
  ModeloTransporte, SolveResultTransporte, ModeloRed, SolveResultRed,
  ModeloEntero, SolveResultEntera, ModeloInventario, SolveResultInventario,
  ModeloDinamico, SolveResultDinamica,
  ResumenSesion, HistorialSesion, ProblemaResueltoHistorial, ModuloActivo,
} from '@/types/io'

// localStorage y no sessionStorage: la conversación vive en PostgreSQL, así que debe
// sobrevivir a cerrar la pestaña igual que sobrevive a reiniciar el backend.
const SESSION_KEY = 'io_sesion_id'

type Modulo = 'lp' | 'transporte' | 'redes' | 'pl-entera' | 'inventario' | 'dinamica'

/** Ruta del workspace de cada módulo del backend. Los nombres no coinciden en dos casos. */
const RUTA_DE_MODULO: Record<ModuloActivo, Modulo> = {
  PL: 'lp',
  ENTERA: 'pl-entera',
  TRANSPORTE: 'transporte',
  REDES: 'redes',
  INVENTARIO: 'inventario',
  DINAMICA: 'dinamica',
}

interface ChatContextValue {
  mensajes: Mensaje[]
  enviar: (texto: string) => Promise<void>
  decidir: (aprobado: boolean, comentario?: string | null) => Promise<void>
  solicitud: SolicitudAprobacion | null
  isSending: boolean
  /** Lo que Pivot está haciendo ahora mismo. null salvo durante un turno en curso. */
  actividad: Actividad | null
  error: string | null
  /** Conversaciones anteriores, la más reciente primero. */
  sesiones: ResumenSesion[]
  sesionActivaId: string | null
  refrescarSesiones: () => Promise<void>
  abrirSesion: (sesionId: string) => Promise<void>
  nuevaConversacion: () => void
}

const ChatContext = createContext<ChatContextValue | null>(null)

/**
 * Deduce a qué módulo pertenece una respuesta del tutor, para que el workspace
 * se adapte automáticamente (LP ⇄ Transporte ⇄ Redes) según lo que el chat detecte.
 */
function moduloDeRespuesta(res: ChatResponse): Modulo | null {
  if (res.resultadoDinamica) return 'dinamica'
  if (res.solicitudAprobacion?.metodo === 'PROGRAMACION_DINAMICA') return 'dinamica'
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

/** Módulo al que pertenece un problema ya resuelto, por su método de resolución. */
function moduloDeProblema(p: ProblemaResueltoHistorial): Modulo {
  switch (p.metodo) {
    case 'TRANSPORTE': return 'transporte'
    case 'REDES': return 'redes'
    case 'BRANCH_AND_BOUND': return 'pl-entera'
    case 'INVENTARIO': return 'inventario'
    case 'PROGRAMACION_DINAMICA': return 'dinamica'
    default: return 'lp' // SIMPLEX, GRAN_M, DOS_FASES, GRAFICO
  }
}

/**
 * Estado del chat compartido por toda la app. Vive por encima de las rutas
 * (montado en AppShell), de modo que cambiar de workspace NO reinicia la
 * conversación. El chat es el orquestador: actualiza el store y navega al
 * módulo correcto cuando el tutor toma decisiones.
 */
/** Cada cuánto se le pregunta al backend qué está haciendo. */
const SONDEO_ACTIVIDAD_MS = 400

export function ChatProvider({ children }: { children: ReactNode }) {
  const [mensajes, setMensajes] = useState<Mensaje[]>([])
  const [isSending, setIsSending] = useState(false)
  const [actividad, setActividad] = useState<Actividad | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [solicitud, setSolicitud] = useState<SolicitudAprobacion | null>(null)
  const [sesiones, setSesiones] = useState<ResumenSesion[]>([])
  const sesionId = useWorkspaceStore(s => s.sesionId)
  const setSesionId = useWorkspaceStore(s => s.setSesionId)
  const store = useWorkspaceStore.getState
  const navigate = useNavigate()
  const location = useLocation()

  async function refrescarSesiones() {
    try {
      setSesiones(await listarSesiones())
    } catch {
      // La lista es accesoria: si el backend no responde, el chat sigue funcionando.
    }
  }

  function irAModulo(modulo: Modulo | null) {
    if (!modulo) return
    if (!location.pathname.startsWith(`/${modulo}`)) {
      navigate(`/${modulo}`)
    }
  }

  /**
   * Repuebla el workspace con el último problema que la sesión resolvió. Solo uno de los
   * seis editores aplica: el que indica el método con el que se resolvió.
   */
  function rehidratarWorkspace(p: ProblemaResueltoHistorial) {
    const s = store()
    switch (p.metodo) {
      case 'SIMPLEX':
      case 'GRAN_M':
      case 'DOS_FASES':
        s.setModelo(p.modelo as ModeloLP)
        s.setResultado(p.resultado as SolveResult)
        break
      case 'GRAFICO':
        s.setModelo(p.modelo as ModeloLP)
        s.setResultadoGrafico(p.resultado as SolveResultGrafico)
        break
      case 'TRANSPORTE':
        s.setModeloTransporte(p.modelo as ModeloTransporte)
        s.setResultadoTransporte(p.resultado as SolveResultTransporte)
        break
      case 'REDES':
        s.setModeloRed(p.modelo as ModeloRed)
        s.setResultadoRed(p.resultado as SolveResultRed)
        break
      case 'BRANCH_AND_BOUND':
        s.setModeloEntero(p.modelo as ModeloEntero)
        s.setResultadoEntero(p.resultado as SolveResultEntera)
        break
      case 'INVENTARIO':
        s.setModeloInventario(p.modelo as ModeloInventario)
        s.setResultadoInventario(p.resultado as SolveResultInventario)
        break
      case 'PROGRAMACION_DINAMICA':
        s.setModeloDinamico(p.modelo as ModeloDinamico)
        s.setResultadoDinamica(p.resultado as SolveResultDinamica)
        break
    }
    s.setStatus('SOLVED')
  }

  /**
   * Deja la app plantada en una conversación: transcript en el chat, último resultado en
   * el workspace y la ruta del módulo que la sesión estaba trabajando.
   */
  function restaurar(historial: HistorialSesion) {
    store().resetWorkspace()
    setSolicitud(null)   // las solicitudes HITL viven en RAM del backend: no sobreviven
    setError(null)
    setSesionId(historial.sesionId)
    localStorage.setItem(SESSION_KEY, historial.sesionId)
    setMensajes(aMensajes(historial.mensajes))

    if (historial.ultimoProblema) {
      rehidratarWorkspace(historial.ultimoProblema)
      irAModulo(moduloDeProblema(historial.ultimoProblema))
    } else if (historial.moduloActivo) {
      irAModulo(RUTA_DE_MODULO[historial.moduloActivo])
    }
  }

  // Al montar: la lista de conversaciones y la que quedó abierta la última vez.
  // Los mensajes viven solo en este estado de React, así que sin esto un F5 los perdía
  // aunque la conversación siguiera viva en el servidor.
  useEffect(() => {
    refrescarSesiones()

    const guardada = localStorage.getItem(SESSION_KEY)
    if (!guardada) return

    let cancelado = false
    obtenerHistorial(guardada)
      .then(historial => {
        if (cancelado) return
        if (historial === null) {
          // El backend ya no conoce la sesión: se descarta y se empieza de cero.
          localStorage.removeItem(SESSION_KEY)
          return
        }
        restaurar(historial)
      })
      .catch(() => {
        // Backend caído o sin red: conservamos el sesionId, el historial sigue en la BD.
        if (!cancelado) setSesionId(guardada)
      })

    return () => { cancelado = true }
    // Solo al montar: restaurar en cada render reabriría la sesión sobre sí misma.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * Sondea qué está haciendo Pivot mientras dura el turno. El POST del chat es
   * bloqueante y no cuenta nada por el camino, así que la fase se consulta aparte.
   *
   * Dos reglas que evitan que el indicador parpadee:
   *  - una respuesta vacía NO borra la fase actual (al arrancar el turno hay una
   *    ventana en la que el POST aún no llegó al servidor y el sondeo da 204);
   *  - una respuesta con `secuencia` menor que la ya pintada se descarta: dos
   *    sondeos en vuelo pueden volver desordenados.
   */
  useEffect(() => {
    if (!isSending || !sesionId) return

    let cancelado = false
    const id = window.setInterval(async () => {
      const nueva = await obtenerActividad(sesionId)
      if (cancelado || !nueva) return
      setActividad(prev => (prev && nueva.secuencia < prev.secuencia ? prev : nueva))
    }, SONDEO_ACTIVIDAD_MS)

    return () => {
      cancelado = true
      window.clearInterval(id)
    }
  }, [isSending, sesionId])

  /** Abre una conversación anterior desde la barra lateral. */
  async function abrirSesion(id: string) {
    if (isSending || id === sesionId) return
    try {
      const historial = await obtenerHistorial(id)
      if (historial === null) {
        setError('Esa conversación ya no existe.')
        await refrescarSesiones()
        return
      }
      restaurar(historial)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo abrir la conversación')
    }
  }

  /**
   * Empieza de cero. No crea nada en el backend: la fila de sesión nace con el primer
   * mensaje, así que un usuario que pulse "nueva" y no escriba no deja basura.
   */
  function nuevaConversacion() {
    if (isSending) return
    store().resetWorkspace()
    setSesionId(null)
    localStorage.removeItem(SESSION_KEY)
    setMensajes([])
    setSolicitud(null)
    setError(null)
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
    if (res.resultadoDinamica) {
      store().setResultadoDinamica(res.resultadoDinamica)
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
    // Ídem para Programación Dinámica: refleja el modelo en el editor del submodelo.
    if (res.solicitudAprobacion?.metodo === 'PROGRAMACION_DINAMICA') {
      store().setModeloDinamico(res.solicitudAprobacion.modelo as ModeloDinamico)
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

    // El id se acuña AQUÍ y no en el servidor. El backend acepta cualquier UUID que
    // le llegue (`esUuid` en AiChatController) y crea la fila de sesión con él; si lo
    // acuñara él, el primer turno de una conversación nueva no tendría sesionId que
    // sondear y la actividad en vivo solo aparecería a partir del segundo mensaje.
    const eraNueva = !sesionId
    const id = sesionId ?? crypto.randomUUID()
    if (eraNueva) setSesionId(id)

    setIsSending(true)
    store().setIsChatBusy(true)
    setError(null)
    try {
      const res = await enviarMensaje(id, texto)
      if (eraNueva) {
        setSesionId(res.sesionId)
        localStorage.setItem(SESSION_KEY, res.sesionId)
      }
      procesarRespuesta(res)
      // El primer turno crea la fila de sesión: aparece en la barra lateral con su
      // título provisional. El definitivo lo escribe el titulador poco después, y lo
      // recoge el siguiente refresco de la lista.
      if (eraNueva) refrescarSesiones()
    } catch (e) {
      // La sesión vive en PostgreSQL: un fallo de red puntual no la invalida.
      // Conservamos el sesionId para poder reintentar sin perder la conversación.
      const msg = e instanceof Error ? e.message : 'Error de conexión'
      setError(msg)
    } finally {
      setIsSending(false)
      setActividad(null)
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
      setActividad(null)
      store().setIsChatBusy(false)
    }
  }

  const value: ChatContextValue = {
    mensajes, enviar, decidir, solicitud, isSending, actividad, error,
    sesiones, sesionActivaId: sesionId, refrescarSesiones, abrirSesion, nuevaConversacion,
  }
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

/** Consume el estado del chat compartido. Debe usarse dentro de <ChatProvider>. */
export function useChat(): ChatContextValue {
  const ctx = useContext(ChatContext)
  if (!ctx) throw new Error('useChat debe usarse dentro de <ChatProvider>')
  return ctx
}
