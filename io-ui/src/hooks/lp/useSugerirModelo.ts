import { useState } from 'react'
import { sugerirModelo } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'

export function useSugerirModelo() {
  const [isSuggesting, setIsSuggesting] = useState(false)
  const [advertencias, setAdvertencias] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const setModelo = useWorkspaceStore(s => s.setModelo)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function sugerir(descripcion: string) {
    setIsSuggesting(true)
    setError(null)
    setStatus('SUGGESTING')
    try {
      const res = await sugerirModelo(descripcion)
      setAdvertencias(res.advertencias)
      if (res.modelo) {
        setModelo(res.modelo)
        setStatus('EDITING')
      } else {
        setStatus('IDLE')
        setError('No se pudo extraer un modelo del enunciado')
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al sugerir modelo')
      setStatus('IDLE')
    } finally {
      setIsSuggesting(false)
    }
  }

  return { sugerir, isSuggesting, advertencias, error }
}
