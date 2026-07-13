import { useState } from 'react'
import { resolverTransporte } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloTransporte } from '@/types/io'

/** Resuelve un modelo de transporte contra el backend y lo vuelca en el store global. */
export function useResolverTransporte() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setModeloTransporte = useWorkspaceStore(s => s.setModeloTransporte)
  const setResultadoTransporte = useWorkspaceStore(s => s.setResultadoTransporte)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloTransporte) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverTransporte(modelo)
      setModeloTransporte(modelo)
      setResultadoTransporte(result)
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
