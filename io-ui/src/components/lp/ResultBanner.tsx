import { formatNum } from '@/lib/utils'
import type { SolveResult } from '@/types/io'

interface Props {
  resultado: SolveResult
}

export function ResultBanner({ resultado }: Props) {
  if (resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') {
    const sol = resultado.solution!
    return (
      <div
        className="rounded-[4px] p-4"
        style={{
          background: 'rgba(106,171,116,0.1)',
          borderLeft: '2px solid var(--ij-green)',
        }}
      >
        <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>
          {resultado.status === 'OPTIMO' ? 'Solución óptima' : 'Soluciones óptimas múltiples'}
        </p>
        <p
          className="mt-1 text-lg"
          style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-green)' }}
        >
          <span style={{ color: 'var(--ij-text-secondary)' }}>Z* = </span>
          {formatNum(sol.valorOptimo)}
        </p>
        <div className="mt-2 flex gap-4 flex-wrap">
          {Object.entries(sol.valores).map(([k, v]) => (
            <span
              key={k}
              className="text-sm"
              style={{ fontFamily: "'JetBrains Mono', monospace" }}
            >
              <span style={{ color: 'var(--ij-purple)' }}>{k}</span>
              <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
              <span style={{ color: 'var(--ij-green)' }}>{formatNum(v)}</span>
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

  const isNoAcotado = resultado.status === 'NO_ACOTADO'
  const accentColor = isNoAcotado ? 'var(--ij-amber)' : 'var(--ij-red)'
  const bgColor = isNoAcotado ? 'rgba(255,200,89,0.08)' : 'rgba(255,82,99,0.08)'

  return (
    <div
      className="rounded-[4px] p-4"
      style={{ background: bgColor, borderLeft: `2px solid ${accentColor}` }}
    >
      <p className="font-semibold text-sm" style={{ color: accentColor }}>
        {resultado.status}
      </p>
      <p className="mt-1 text-sm" style={{ color: accentColor, opacity: 0.85 }}>
        {msgs[resultado.status] ?? 'Estado desconocido'}
      </p>
    </div>
  )
}
