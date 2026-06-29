import { formatNum } from '@/lib/utils'
import type { SolveResult, RangoCoeficiente, RangoRHS } from '@/types/io'

interface Props {
  resultado: SolveResult
}

function formatRango(val: number | null, infinito: string): string {
  return val === null ? infinito : formatNum(val)
}

function SensibilidadTable({
  coeficientes,
  rhs,
}: {
  coeficientes: RangoCoeficiente[]
  rhs: RangoRHS[]
}) {
  const thStyle: React.CSSProperties = {
    color: 'var(--ij-text-secondary)',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: '11px',
    fontWeight: 600,
    paddingBottom: '4px',
    textAlign: 'left',
  }
  const tdStyle: React.CSSProperties = {
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: '11px',
    paddingTop: '2px',
    paddingBottom: '2px',
    paddingRight: '16px',
    color: 'var(--ij-text-primary)',
  }
  const varColor: React.CSSProperties = { color: 'var(--ij-purple)' }
  const numColor: React.CSSProperties = { color: 'var(--ij-teal)' }

  return (
    <div className="mt-3 space-y-4">
      <div>
        <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
          Coeficientes de la función objetivo
        </p>
        <table style={{ borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={thStyle}>Variable</th>
              <th style={thStyle}>Actual</th>
              <th style={thStyle}>Mín</th>
              <th style={thStyle}>Máx</th>
            </tr>
          </thead>
          <tbody>
            {coeficientes.map(r => (
              <tr key={r.variable}>
                <td style={{ ...tdStyle, ...varColor }}>{r.variable}</td>
                <td style={{ ...tdStyle, ...numColor }}>{formatNum(r.valorActual)}</td>
                <td style={tdStyle}>{formatRango(r.min, '-∞')}</td>
                <td style={tdStyle}>{formatRango(r.max, '+∞')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div>
        <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
          Lado derecho (RHS)
        </p>
        <table style={{ borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={thStyle}>Restricción</th>
              <th style={thStyle}>Actual</th>
              <th style={thStyle}>Mín</th>
              <th style={thStyle}>Máx</th>
            </tr>
          </thead>
          <tbody>
            {rhs.map(r => (
              <tr key={r.restriccion}>
                <td style={{ ...tdStyle, ...varColor }}>{r.restriccion}</td>
                <td style={{ ...tdStyle, ...numColor }}>{formatNum(r.valorActual)}</td>
                <td style={tdStyle}>{formatRango(r.min, '-∞')}</td>
                <td style={tdStyle}>{formatRango(r.max, '+∞')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function ResultBanner({ resultado }: Props) {
  if (resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') {
    const sol = resultado.solution!
    const holguras = Object.entries(sol.holguras ?? {})
    const preciosSombra = Object.entries(sol.preciosSombra ?? {})
    const rangos = sol.rangosSensibilidad

    return (
      <div
        className="rounded-[4px] p-4 space-y-3"
        style={{
          background: 'rgba(106,171,116,0.1)',
          borderLeft: '2px solid var(--ij-green)',
        }}
      >
        {/* Encabezado y valor óptimo */}
        <div>
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
        </div>

        {/* Variables de decisión */}
        <div>
          <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
            Variables de decisión
          </p>
          <div className="flex gap-4 flex-wrap">
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

        {/* Holguras */}
        {holguras.length > 0 && (
          <div>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
              Holguras <span style={{ fontWeight: 400 }}>(0 = restricción activa)</span>
            </p>
            <div className="flex gap-4 flex-wrap">
              {holguras.map(([k, v]) => (
                <span
                  key={k}
                  className="text-sm"
                  style={{ fontFamily: "'JetBrains Mono', monospace" }}
                >
                  <span style={{ color: 'var(--ij-cyan)' }}>{k}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
                  <span style={{ color: v === 0 ? 'var(--ij-amber)' : 'var(--ij-text-primary)' }}>
                    {formatNum(v)}
                  </span>
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Precios sombra */}
        {preciosSombra.length > 0 && (
          <div>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
              Precios sombra <span style={{ fontWeight: 400 }}>(∂Z*/∂bᵢ)</span>
            </p>
            <div className="flex gap-4 flex-wrap">
              {preciosSombra.map(([k, v]) => (
                <span
                  key={k}
                  className="text-sm"
                  style={{ fontFamily: "'JetBrains Mono', monospace" }}
                >
                  <span style={{ color: 'var(--ij-orange)' }}>{k}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
                  <span style={{ color: 'var(--ij-text-primary)' }}>{formatNum(v)}</span>
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Análisis de sensibilidad */}
        {rangos && (
          <details>
            <summary
              style={{
                fontSize: '11px',
                color: 'var(--ij-text-secondary)',
                cursor: 'pointer',
                userSelect: 'none',
                fontWeight: 600,
                listStyle: 'none',
              }}
            >
              ▸ Análisis de sensibilidad
            </summary>
            <SensibilidadTable
              coeficientes={rangos.coeficientesObjetivo}
              rhs={rangos.rhs}
            />
          </details>
        )}
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
