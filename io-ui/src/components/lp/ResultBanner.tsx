import { motion } from 'motion/react'
import { ValorAnimado } from '@/components/shared/ValorAnimado'
import { revealChip, revealRow, stagger, T_BASE } from '@/lib/motion'
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
          <motion.tbody variants={stagger(0.03)} initial="hidden" animate="visible">
            {coeficientes.map(r => (
              <motion.tr key={r.variable} variants={revealRow}>
                <td style={{ ...tdStyle, ...varColor }}>{r.variable}</td>
                <td style={{ ...tdStyle, ...numColor }}>{formatNum(r.valorActual)}</td>
                <td style={tdStyle}>{formatRango(r.min, '-∞')}</td>
                <td style={tdStyle}>{formatRango(r.max, '+∞')}</td>
              </motion.tr>
            ))}
          </motion.tbody>
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
          <motion.tbody variants={stagger(0.03)} initial="hidden" animate="visible">
            {rhs.map(r => (
              <motion.tr key={r.restriccion} variants={revealRow}>
                <td style={{ ...tdStyle, ...varColor }}>{r.restriccion}</td>
                <td style={{ ...tdStyle, ...numColor }}>{formatNum(r.valorActual)}</td>
                <td style={tdStyle}>{formatRango(r.min, '-∞')}</td>
                <td style={tdStyle}>{formatRango(r.max, '+∞')}</td>
              </motion.tr>
            ))}
          </motion.tbody>
        </table>
      </div>
    </div>
  )
}

/** Grupo de fichas `nombre = valor` que entran escalonadas. */
function GrupoChips({
  titulo,
  nota,
  items,
  colorClave,
  colorValor,
}: {
  titulo: string
  nota?: string
  items: [string, number][]
  colorClave: string
  colorValor: (v: number) => string
}) {
  return (
    <div>
      <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
        {titulo} {nota && <span style={{ fontWeight: 400 }}>{nota}</span>}
      </p>
      <motion.div
        className="flex gap-4 flex-wrap"
        variants={stagger(0.05, 0.1)}
        initial="hidden"
        animate="visible"
      >
        {items.map(([k, v]) => (
          <motion.span
            key={k}
            variants={revealChip}
            className="text-sm"
            style={{ fontFamily: "'JetBrains Mono', monospace" }}
          >
            <span style={{ color: colorClave }}>{k}</span>
            <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
            <span style={{ color: colorValor(v) }}>{formatNum(v)}</span>
          </motion.span>
        ))}
      </motion.div>
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
      <motion.div
        className="rounded-[4px] p-4 space-y-3"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={T_BASE}
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
            <ValorAnimado value={sol.valorOptimo} />
          </p>
        </div>

        <GrupoChips
          titulo="Variables de decisión"
          items={Object.entries(sol.valores)}
          colorClave="var(--ij-purple)"
          colorValor={() => 'var(--ij-green)'}
        />

        {holguras.length > 0 && (
          <GrupoChips
            titulo="Holguras"
            nota="(0 = restricción activa)"
            items={holguras}
            colorClave="var(--ij-cyan)"
            colorValor={v => (v === 0 ? 'var(--ij-amber)' : 'var(--ij-text-primary)')}
          />
        )}

        {preciosSombra.length > 0 && (
          <GrupoChips
            titulo="Precios sombra"
            nota="(∂Z*/∂bᵢ)"
            items={preciosSombra}
            colorClave="var(--ij-orange)"
            colorValor={() => 'var(--ij-text-primary)'}
          />
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
      </motion.div>
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
    <motion.div
      className="rounded-[4px] p-4"
      initial={{ opacity: 0, x: -6 }}
      animate={{ opacity: 1, x: 0 }}
      transition={T_BASE}
      style={{ background: bgColor, borderLeft: `2px solid ${accentColor}` }}
    >
      <p className="font-semibold text-sm" style={{ color: accentColor }}>
        {resultado.status}
      </p>
      <p className="mt-1 text-sm" style={{ color: accentColor, opacity: 0.85 }}>
        {msgs[resultado.status] ?? 'Estado desconocido'}
      </p>
    </motion.div>
  )
}
