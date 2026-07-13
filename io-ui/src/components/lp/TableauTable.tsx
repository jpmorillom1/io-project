import { motion } from 'motion/react'
import { cn, formatNum } from '@/lib/utils'
import { revealRow, stagger, T_BASE } from '@/lib/motion'
import type { SolveStep, TableauHighlights } from '@/types/io'

interface Props {
  step: SolveStep
  highlights: TableauHighlights
}

const PIVOTE_SUAVE = 'inset 0 0 0 1px rgba(81,200,207,0.4)'
const PIVOTE_FUERTE = 'inset 0 0 0 2px rgba(81,200,207,0.95)'

export function TableauTable({ step, highlights }: Props) {
  const { encabezados, tableau, base } = step.datos
  const { columnaEntrada, filaSalida, celdaPivote } = highlights
  const m = tableau.length - 1

  // El escalonado se reinicia en cada paso: `key` fuerza el remontaje del tbody.
  const filas = tableau.length
  const retardoPivote = 0.04 * filas + 0.1

  return (
    <div className="overflow-x-auto">
      <table
        className="min-w-full border-collapse"
        style={{
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: '13px',
          background: 'var(--ij-bg-editor)',
        }}
      >
        <thead>
          <tr>
            <th
              className="px-3 py-2 text-left font-normal"
              style={{
                color: 'var(--ij-text-secondary)',
                fontSize: '12px',
                borderBottom: '1px solid var(--ij-border)',
              }}
            >
              base
            </th>
            {encabezados.map((h, j) => {
              const isEntrada = columnaEntrada === j
              return (
                <motion.th
                  key={j}
                  className="px-3 py-2 text-center"
                  animate={{
                    color: isEntrada ? 'var(--ij-cyan)' : 'var(--ij-text-secondary)',
                    backgroundColor: isEntrada ? 'rgba(81,200,207,0.08)' : 'rgba(81,200,207,0)',
                    borderBottomColor: isEntrada ? 'var(--ij-cyan)' : 'var(--ij-border)',
                  }}
                  transition={T_BASE}
                  style={{
                    fontSize: '12px',
                    fontWeight: isEntrada ? 700 : 400,
                    borderBottomWidth: '1px',
                    borderBottomStyle: 'solid',
                  }}
                >
                  {h}
                </motion.th>
              )
            })}
          </tr>
        </thead>

        <motion.tbody
          key={step.numero}
          variants={stagger(0.04)}
          initial="hidden"
          animate="visible"
        >
          {tableau.map((fila, i) => {
            const isZRow = i === m
            const isFilaSalida = !isZRow && filaSalida === i
            return (
              <motion.tr
                key={i}
                variants={revealRow}
                style={{ background: isZRow ? 'var(--ij-bg-hover)' : undefined }}
              >
                <td
                  className={cn('px-3 py-1.5 font-medium')}
                  style={{
                    color: isZRow ? 'var(--ij-text-secondary)' : 'var(--ij-purple)',
                    fontStyle: isZRow ? 'italic' : undefined,
                    borderBottom: '1px solid var(--ij-border)',
                    borderLeft: isFilaSalida ? '2px solid var(--ij-orange)' : undefined,
                  }}
                >
                  {isZRow ? 'z' : base[i]}
                </td>
                {fila.map((val, j) => {
                  const isPivote =
                    celdaPivote !== null && celdaPivote[0] === i && celdaPivote[1] === j
                  const isEntrada = columnaEntrada === j

                  let bg: string | undefined
                  let color = isZRow ? 'var(--ij-text-secondary)' : 'var(--ij-cyan)'
                  let fontWeight: number | undefined
                  let fontStyle: string | undefined

                  if (isPivote) {
                    bg = 'rgba(81,200,207,0.18)'
                    color = 'var(--ij-cyan)'
                    fontWeight = 700
                  } else if (isFilaSalida) {
                    bg = 'rgba(192,148,104,0.08)'
                    color = 'var(--ij-orange)'
                  } else if (isEntrada) {
                    bg = 'rgba(81,200,207,0.08)'
                  }

                  if (isZRow && !isPivote) {
                    fontStyle = 'italic'
                  }

                  return (
                    <motion.td
                      key={j}
                      className="px-3 py-1.5 text-center tabular-nums"
                      // El pivote late una vez, ya asentada la tabla: señala dónde giró el algoritmo.
                      animate={
                        isPivote
                          ? { boxShadow: [PIVOTE_SUAVE, PIVOTE_FUERTE, PIVOTE_SUAVE] }
                          : undefined
                      }
                      transition={
                        isPivote
                          ? { duration: 0.9, delay: retardoPivote, times: [0, 0.35, 1] }
                          : undefined
                      }
                      style={{
                        background: bg,
                        color,
                        fontWeight,
                        fontStyle,
                        borderBottom: '1px solid var(--ij-border)',
                      }}
                    >
                      {formatNum(val)}
                    </motion.td>
                  )
                })}
              </motion.tr>
            )
          })}
        </motion.tbody>
      </table>
    </div>
  )
}
