import { useState } from 'react'
import { resolverBranchAndBound } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloEntero } from '@/types/io'

/** Resuelve un modelo de PL Entera (B&B) contra el backend y lo vuelca en el store global. */
export function useResolverEntera() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setModeloEntero = useWorkspaceStore(s => s.setModeloEntero)
  const setResultadoEntero = useWorkspaceStore(s => s.setResultadoEntero)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloEntero) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverBranchAndBound(modelo)
      setModeloEntero(modelo)
      setResultadoEntero(result)
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
