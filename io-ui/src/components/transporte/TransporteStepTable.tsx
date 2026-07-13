import { type CSSProperties } from 'react'
import { motion } from 'motion/react'
import { revealRow, stagger } from '@/lib/motion'
import { formatNum } from '@/lib/utils'
import type { StepDatosTransporte } from '@/types/io'

const ENTRA_SUAVE = 'inset 0 0 0 1px rgba(20,196,182,0.45)'
const ENTRA_FUERTE = 'inset 0 0 0 2px rgba(20,196,182,0.95)'

interface Props {
  datos: StepDatosTransporte
}

/**
 * Tabla de una iteración de transporte. Espeja el estilo de TableauTable (LP):
 * monoespaciado, bordes var(--ij-border), encabezados en gris y celdas resaltadas.
 * Muestra costo/asignación por celda y, según el método, u/v, penalizaciones,
 * costos reducidos y el ciclo stepping-stone.
 */
export function TransporteStepTable({ datos }: Props) {
  const { origenes, destinos, costos, oferta, demanda, asignaciones } = datos
  const tieneUV = Array.isArray(datos.u) && Array.isArray(datos.v)
  const tienePenal = Array.isArray(datos.penalizacionesFila)

  const th: CSSProperties = {
    padding: '6px 10px', textAlign: 'center', fontWeight: 400,
    color: 'var(--ij-text-secondary)', fontSize: '12px',
    borderBottom: '1px solid var(--ij-border)',
  }
  const td: CSSProperties = {
    padding: '6px 10px', textAlign: 'center',
    borderBottom: '1px solid var(--ij-border)',
  }

  function enCiclo(i: number, j: number) {
    return (datos.ciclo ?? []).some(c => c[0] === i && c[1] === j)
  }
  function es(celda: [number, number] | undefined, i: number, j: number) {
    return celda != null && celda[0] === i && celda[1] === j
  }

  return (
    <div className="overflow-x-auto">
      <table
        className="border-collapse"
        style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '13px', background: 'var(--ij-bg-editor)' }}
      >
        <thead>
          <tr>
            <th style={{ ...th, textAlign: 'left' }}>ruta</th>
            {destinos.map((d, j) => (
              <th key={j} style={th}>
                <span style={{ color: 'var(--ij-purple)' }}>{d}</span>
                {tieneUV && (
                  <div style={{ fontSize: '11px', color: 'var(--ij-orange)' }}>v={formatNum(datos.v![j])}</div>
                )}
              </th>
            ))}
            <th style={{ ...th, color: 'var(--ij-orange)' }}>oferta</th>
            {tienePenal && <th style={{ ...th, color: 'var(--ij-cyan)' }}>pen.</th>}
          </tr>
        </thead>
        <motion.tbody variants={stagger(0.04)} initial="hidden" animate="visible">
          {origenes.map((o, i) => (
            <motion.tr key={i} variants={revealRow}>
              <td style={{ ...td, textAlign: 'left', color: 'var(--ij-purple)' }}>
                {o}
                {tieneUV && (
                  <span style={{ fontSize: '11px', color: 'var(--ij-orange)' }}> · u={formatNum(datos.u![i])}</span>
                )}
              </td>
              {destinos.map((_, j) => {
                const asignado = asignaciones[i]?.[j]
                const reducido = datos.costosReducidos?.[i]?.[j]
                const cic = enCiclo(i, j)
                const entra = es(datos.celdaEntrante, i, j) || es(datos.celda, i, j)
                const sale = es(datos.celdaSaliente, i, j)

                let bg: string | undefined
                let boxShadow: string | undefined
                if (entra) { bg = 'rgba(20,196,182,0.18)'; boxShadow = ENTRA_SUAVE }
                else if (sale) { bg = 'rgba(192,148,104,0.14)' }
                else if (cic) { bg = 'rgba(20,196,182,0.06)' }

                const usada = asignado != null && asignado > 0
                return (
                  <motion.td
                    key={j}
                    style={{ ...td, background: bg, boxShadow }}
                    // La celda entrante late una vez, ya asentada la tabla.
                    animate={entra ? { boxShadow: [ENTRA_SUAVE, ENTRA_FUERTE, ENTRA_SUAVE] } : undefined}
                    transition={
                      entra
                        ? { duration: 0.9, delay: 0.04 * origenes.length + 0.1, times: [0, 0.35, 1] }
                        : undefined
                    }
                  >
                    <div style={{ fontSize: '10px', color: 'var(--ij-text-muted)' }}>c={formatNum(costos[i][j])}</div>
                    <div style={{ fontWeight: usada ? 700 : 400, color: usada ? 'var(--ij-cyan)' : 'var(--ij-text-muted)' }}>
                      {asignado != null ? formatNum(asignado) : '·'}
                    </div>
                    {reducido != null && (
                      <div style={{ fontSize: '10px', color: reducido < 0 ? 'var(--ij-orange)' : 'var(--ij-text-muted)' }}>
                        Δ={formatNum(reducido)}
                      </div>
                    )}
                  </motion.td>
                )
              })}
              <td style={{ ...td, fontWeight: 600, color: 'var(--ij-orange)' }}>{formatNum(oferta[i])}</td>
              {tienePenal && (
                <td style={{ ...td, color: 'var(--ij-cyan)' }}>{penal(datos.penalizacionesFila, i)}</td>
              )}
            </motion.tr>
          ))}
          <motion.tr variants={revealRow}>
            <td style={{ ...td, textAlign: 'left', color: 'var(--ij-teal)' }}>demanda</td>
            {demanda.map((d, j) => (
              <td key={j} style={{ ...td, fontWeight: 600, color: 'var(--ij-teal)' }}>{formatNum(d)}</td>
            ))}
            <td style={td} />
            {tienePenal && <td style={td} />}
          </motion.tr>
          {tienePenal && (
            <motion.tr variants={revealRow}>
              <td style={{ ...td, textAlign: 'left', color: 'var(--ij-cyan)' }}>pen.</td>
              {destinos.map((_, j) => (
                <td key={j} style={{ ...td, color: 'var(--ij-cyan)' }}>{penal(datos.penalizacionesColumna, j)}</td>
              ))}
              <td style={td} />
              <td style={td} />
            </motion.tr>
          )}
        </motion.tbody>
      </table>
    </div>
  )
}

function penal(arr: (number | null)[] | undefined, idx: number): string {
  const v = arr?.[idx]
  return v == null ? '—' : formatNum(v)
}
