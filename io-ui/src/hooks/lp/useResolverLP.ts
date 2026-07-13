import { useState } from 'react'
import { resolverSimplex, resolverDosFases } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { detectarMetodoLP, type MetodoLP } from '@/lib/lp/detectarMetodo'
import type { ModeloLP } from '@/types/io'

export function useResolverLP() {
  const [isSolving, setIsSolving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [metodo, setMetodo] = useState<MetodoLP | null>(null)
  const setResultado = useWorkspaceStore(s => s.setResultado)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function resolver(modelo: ModeloLP) {
    const metodoDetectado = detectarMetodoLP(modelo)
    setIsSolving(true)
    setError(null)
    setStatus('SOLVING')
    try {
      const result = metodoDetectado === 'SIMPLEX'
        ? await resolverSimplex(modelo)
        : await resolverDosFases(modelo)
      setMetodo(metodoDetectado)
      setResultado(result)
      setStatus('SOLVED')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al resolver')
      setStatus('EDITING')
    } finally {
      setIsSolving(false)
    }
  }

  return { resolver, isSolving, error, metodo }
}
