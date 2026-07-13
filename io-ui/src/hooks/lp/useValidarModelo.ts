import { useState } from 'react'
import { validarModelo } from '@/api/io'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloLP } from '@/types/io'

export function useValidarModelo() {
  const [isValidating, setIsValidating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Lee del store para que las actualizaciones del chat también sean visibles.
  const errores = useWorkspaceStore(s => s.erroresValidacion)
  const sugerencias = useWorkspaceStore(s => s.sugerenciasValidacion)
  const setValidacion = useWorkspaceStore(s => s.setValidacion)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  async function validar(descripcion: string, modelo: ModeloLP) {
    setIsValidating(true)
    setError(null)
    setStatus('VALIDATING')
    try {
      const res = await validarModelo(descripcion, modelo)
      setValidacion(res)
      setStatus(res.esValido ? 'EDITING' : 'INVALID')
      return res
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al validar')
      setStatus('EDITING')
      return null
    } finally {
      setIsValidating(false)
    }
  }

  return { validar, isValidating, errores, sugerencias, error }
}
