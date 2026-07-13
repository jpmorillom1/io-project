import { formatNum } from '@/lib/utils'
import type { SolveResultEntera } from '@/types/io'

interface Props {
  resultado: SolveResultEntera
}

const MONO = { fontFamily: "'JetBrains Mono', monospace" } as const

/**
 * Banner de resultado de PL Entera. Caja verde con Z* entero, la solución por
 * variable y la brecha de integralidad (por qué NO basta con redondear la
 * relajación); caja roja para INFACTIBLE/NO_ACOTADO.
 */
export function EnteraResultBanner({ resultado }: Props) {
  if ((resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') && resultado.solution) {
    const sol = resultado.solution
    const brecha = Math.abs(sol.valorRelajacion - sol.valorOptimo)
    return (
      <div
        className="rounded-[4px] p-4 space-y-3"
        style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
      >
        <div>
          <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>
            {resultado.status === 'OPTIMO' ? 'Solución óptima entera' : 'Óptimo entero (múltiple)'}
          </p>
          <p className="mt-1 text-lg" style={{ ...MONO, color: 'var(--ij-green)' }}>
            <span style={{ color: 'var(--ij-text-secondary)' }}>Z* = </span>
            {formatNum(sol.valorOptimo)}
          </p>
        </div>

        <Detalle titulo="Solución entera">
          <div className="flex gap-4 flex-wrap">
            {Object.entries(sol.valores).map(([variable, valor]) => (
              <span key={variable} className="text-sm" style={MONO}>
                <span style={{ color: 'var(--ij-purple)' }}>{variable}</span>
                <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
                <span style={{ color: 'var(--ij-text-primary)' }}>{formatNum(valor)}</span>
              </span>
            ))}
          </div>
        </Detalle>

        <Detalle titulo="Brecha de integralidad">
          <p className="text-sm" style={{ ...MONO, color: 'var(--ij-text-secondary)' }}>
            Relajación LP de la raíz ={' '}
            <span style={{ color: 'var(--ij-cyan)' }}>{formatNum(sol.valorRelajacion)}</span>
            {' · '}brecha ={' '}
            <span style={{ color: 'var(--ij-amber)' }}>{formatNum(brecha)}</span>
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--ij-text-secondary)' }}>
            {brecha === 0
              ? 'La relajación ya era entera: redondear habría bastado en este caso.'
              : 'Por eso no basta con redondear la relajación: el óptimo entero se obtiene ramificando.'}
          </p>
        </Detalle>

        <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
          Nodos explorados: <span style={{ ...MONO, color: 'var(--ij-text-primary)' }}>{sol.nodosExplorados}</span>
        </p>
      </div>
    )
  }

  const msgs: Record<string, string> = {
    INFACTIBLE: 'No existe ninguna solución entera factible para el modelo.',
    NO_ACOTADO: 'La relajación no está acotada: la función objetivo crece sin límite.',
    ERROR: 'El solver encontró un error interno al procesar el modelo.',
  }
  return (
    <div className="rounded-[4px] p-4" style={{ background: 'rgba(255,82,99,0.08)', borderLeft: '2px solid var(--ij-red)' }}>
      <p className="font-semibold text-sm" style={{ color: 'var(--ij-red)' }}>{resultado.status}</p>
      <p className="mt-1 text-sm" style={{ color: 'var(--ij-red)', opacity: 0.85 }}>
        {msgs[resultado.status] ?? 'Estado desconocido'}
      </p>
    </div>
  )
}

function Detalle({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <div>
      <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
        {titulo}
      </p>
      {children}
    </div>
  )
}
