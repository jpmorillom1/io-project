import { useState } from 'react'
import { resolverInventario } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloInventario, MetodoInventario } from '@/types/io'

/** Resuelve un modelo de inventario contra el backend y lo vuelca en el store global. */
export function useResolverInventario() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setModeloInventario = useWorkspaceStore(s => s.setModeloInventario)
  const setResultadoInventario = useWorkspaceStore(s => s.setResultadoInventario)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(metodo: MetodoInventario, modelo: ModeloInventario) {
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = await resolverInventario(metodo, modelo)
      setModeloInventario(modelo)
      setResultadoInventario(result)
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
