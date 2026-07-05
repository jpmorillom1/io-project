import { formatNum } from '@/lib/utils'
import type { SolveResultTransporte, MetodoTransporte } from '@/types/io'

interface Props {
  resultado: SolveResultTransporte
}

const METODO_LABEL: Record<MetodoTransporte, string> = {
  ESQUINA_NOROESTE: 'Esquina Noroeste',
  COSTO_MINIMO: 'Costo Mínimo',
  VOGEL: 'Vogel (VAM)',
  MODI: 'MODI',
}

/**
 * Banner de resultado de transporte. Espeja ResultBanner (LP): caja verde con
 * el costo total, la comparativa de métodos iniciales y la matriz de asignaciones.
 */
export function TransporteResultBanner({ resultado }: Props) {
  if (resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') {
    const sol = resultado.solution!
    return (
      <div
        className="rounded-[4px] p-4 space-y-3"
        style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
      >
        <div>
          <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>
            {resultado.status === 'OPTIMO' ? 'Solución óptima' : 'Soluciones óptimas múltiples'}
          </p>
          <p className="mt-1 text-lg" style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-green)' }}>
            <span style={{ color: 'var(--ij-text-secondary)' }}>Costo total = </span>
            {formatNum(sol.costoTotal)}
          </p>
        </div>

        {sol.comparativaInicial && sol.comparativaInicial.length > 0 && (
          <div>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '6px' }}>
              Comparativa de soluciones iniciales
            </p>
            <div className="flex gap-4 flex-wrap">
              {sol.comparativaInicial.map(c => {
                const arranque = sol.metodoInicial === c.metodo
                return (
                  <span key={c.metodo} className="text-sm" style={{ fontFamily: "'JetBrains Mono', monospace" }}>
                    <span style={{ color: arranque ? 'var(--ij-teal)' : 'var(--ij-purple)' }}>
                      {METODO_LABEL[c.metodo]}
                    </span>
                    <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
                    <span style={{ color: 'var(--ij-text-primary)' }}>{formatNum(c.costoInicial)}</span>
                    {arranque && <span style={{ color: 'var(--ij-teal)' }}> ★</span>}
                  </span>
                )
              })}
            </div>
            <p className="mt-1.5" style={{ fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
              MODI arrancó desde la más barata (★) y la optimizó hasta el óptimo.
            </p>
          </div>
        )}

        <div>
          <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
            Asignaciones finales
          </p>
          <div className="flex flex-col gap-0.5">
            {rutasUsadas(sol).map((r, i) => (
              <span key={i} className="text-sm" style={{ fontFamily: "'JetBrains Mono', monospace" }}>
                <span style={{ color: 'var(--ij-purple)' }}>{r.origen}</span>
                <span style={{ color: 'var(--ij-text-secondary)' }}>{' → '}</span>
                <span style={{ color: 'var(--ij-purple)' }}>{r.destino}</span>
                <span style={{ color: 'var(--ij-text-secondary)' }}>{' : '}</span>
                <span style={{ color: 'var(--ij-green)' }}>{formatNum(r.cantidad)}</span>
              </span>
            ))}
          </div>
        </div>
      </div>
    )
  }

  const msgs: Record<string, string> = {
    INFACTIBLE: 'El problema es infactible: no existe una asignación que satisfaga oferta y demanda.',
    NO_ACOTADO: 'El problema no está acotado.',
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

function rutasUsadas(sol: NonNullable<SolveResultTransporte['solution']>) {
  const rutas: { origen: string; destino: string; cantidad: number }[] = []
  sol.asignaciones.forEach((fila, i) =>
    fila.forEach((cant, j) => {
      if (cant > 1e-9) rutas.push({ origen: sol.origenes[i], destino: sol.destinos[j], cantidad: cant })
    })
  )
  return rutas
}
