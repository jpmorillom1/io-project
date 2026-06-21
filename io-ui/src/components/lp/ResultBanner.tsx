import { formatNum } from '@/lib/utils'
import type { SolveResult } from '@/types/io'

interface Props {
  resultado: SolveResult
}

export function ResultBanner({ resultado }: Props) {
  if (resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') {
    const sol = resultado.solution!
    return (
      <div className="rounded-lg border border-green-200 bg-green-50 p-4">
        <p className="font-semibold text-green-800 text-sm">
          {resultado.status === 'OPTIMO' ? 'Solución óptima' : 'Soluciones óptimas múltiples'}
        </p>
        <p className="mt-1 text-green-700 text-lg font-mono">
          Z* = {formatNum(sol.valorOptimo)}
        </p>
        <div className="mt-2 flex gap-4 flex-wrap">
          {Object.entries(sol.valores).map(([k, v]) => (
            <span key={k} className="text-sm text-green-700 font-mono">
              {k} = {formatNum(v)}
            </span>
          ))}
        </div>
      </div>
    )
  }

  const msgs: Record<string, string> = {
    INFACTIBLE: 'El problema es infactible: no existe solución que satisfaga todas las restricciones.',
    NO_ACOTADO: 'El problema no está acotado: la función objetivo puede crecer indefinidamente.',
    ERROR: 'El solver encontró un error interno al procesar el modelo.',
  }

  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4">
      <p className="font-semibold text-red-800 text-sm">{resultado.status}</p>
      <p className="mt-1 text-sm text-red-700">{msgs[resultado.status] ?? 'Estado desconocido'}</p>
    </div>
  )
}
