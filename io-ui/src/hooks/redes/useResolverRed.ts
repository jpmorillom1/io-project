import { useState } from 'react'
import { resolverRed } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloRed } from '@/types/io'

/** Resuelve un modelo de redes contra el backend y lo vuelca en el store global. */
export function useResolverRed() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setModeloRed = useWorkspaceStore(s => s.setModeloRed)
  const setResultadoRed = useWorkspaceStore(s => s.setResultadoRed)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloRed) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverRed(modelo)
      setModeloRed(modelo)
      setResultadoRed(result)
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
