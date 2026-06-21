import { useState, useEffect } from 'react'
import { enviarMensaje } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { Mensaje } from '@/types/io'

const SESSION_KEY = 'io_sesion_id'

export function useChat() {
  const [mensajes, setMensajes] = useState<Mensaje[]>([])
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const sesionId = useWorkspaceStore(s => s.sesionId)
  const setSesionId = useWorkspaceStore(s => s.setSesionId)
  const store = useWorkspaceStore.getState

  useEffect(() => {
    const stored = sessionStorage.getItem(SESSION_KEY)
    if (stored) setSesionId(stored)
  }, [setSesionId])

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

      // El chat es el orquestador: cuando el tutor toma decisiones, actualiza
      // el formulario y el tableau automáticamente sin intervención del usuario.
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

      const msgTutor: Mensaje = { rol: 'tutor', texto: res.respuesta, timestamp: Date.now() }
      setMensajes(prev => [...prev, msgTutor])
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

  return { mensajes, enviar, isSending, error }
}
