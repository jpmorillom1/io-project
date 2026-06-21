import { useState } from 'react'
import { resolverSimplex } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloLP } from '@/types/io'

export function useSimplex() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setResultado = useWorkspaceStore(s => s.setResultado)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloLP) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverSimplex(modelo)
      setResultado(result)
      setStatus('SOLVED')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al resolver')
      setStatus('EDITING')
    } finally {
      setIsSolving(false)
    }
  }

  return { resolver, isSolving, error }
}
