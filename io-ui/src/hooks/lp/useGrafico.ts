import { useState } from 'react'
import { resolverGrafico } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloLP } from '@/types/io'

export function useGrafico() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setResultadoGrafico = useWorkspaceStore(s => s.setResultadoGrafico)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloLP) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverGrafico(modelo)
      setResultadoGrafico(result)
      setStatus('SOLVED')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al graficar')
      setStatus('EDITING')
    } finally {
      setIsSolving(false)
    }
  }

  return { resolver, isSolving, error }
}
