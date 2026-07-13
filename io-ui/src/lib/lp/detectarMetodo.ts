import type { ModeloLP } from '@/types/io'

export type MetodoLP = 'SIMPLEX' | 'DOS_FASES'

/**
 * Simplex estándar solo admite restricciones ≤ con b ≥ 0 (ver SimplexSolver.validar
 * en el backend). Cualquier restricción ≥ o = — o un b negativo — requiere Dos Fases,
 * que es el método predeterminado del tutor para esos casos (ver tutor_system_prompt.txt).
 */
export function detectarMetodoLP(modelo: ModeloLP): MetodoLP {
  const esSimplexEstandar = modelo.restricciones.every(
    r => r.tipo === 'LEQ' && r.rhs >= 0
  )
  return esSimplexEstandar ? 'SIMPLEX' : 'DOS_FASES'
}
