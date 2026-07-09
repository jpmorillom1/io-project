import { useState } from 'react'
import { resolverDinamica } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloDinamico, MetodoDinamico } from '@/types/io'

/** Resuelve un modelo de Programación Dinámica contra el backend y lo vuelca en el store global. */
export function useResolverDinamica() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setModeloDinamico = useWorkspaceStore(s => s.setModeloDinamico)
  const setResultadoDinamica = useWorkspaceStore(s => s.setResultadoDinamica)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(metodo: MetodoDinamico, modelo: ModeloDinamico) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverDinamica(metodo, modelo)
      setModeloDinamico(modelo)
      setResultadoDinamica(result)
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
