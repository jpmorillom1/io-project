import { cn, formatNum } from '@/lib/utils'
import type { SolveStep, TableauHighlights } from '@/types/io'

interface Props {
  step: SolveStep
  highlights: TableauHighlights
}

export function TableauTable({ step, highlights }: Props) {
  const { encabezados, tableau, base } = step.datos
  const { columnaEntrada, filaSalida, celdaPivote } = highlights
  const m = tableau.length - 1

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
                <th
                  key={j}
                  className="px-3 py-2 text-center"
                  style={{
                    color: isEntrada ? 'var(--ij-cyan)' : 'var(--ij-text-secondary)',
                    background: isEntrada ? 'rgba(81,200,207,0.08)' : undefined,
                    fontSize: '12px',
                    fontWeight: isEntrada ? 700 : 400,
                    borderBottom: isEntrada
                      ? '1px solid var(--ij-cyan)'
                      : '1px solid var(--ij-border)',
                  }}
                >
                  {h}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {tableau.map((fila, i) => {
            const isZRow = i === m
            const isFilaSalida = !isZRow && filaSalida === i
            return (
              <tr
                key={i}
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
                  let boxShadow: string | undefined

                  if (isPivote) {
                    bg = 'rgba(81,200,207,0.18)'
                    color = 'var(--ij-cyan)'
                    fontWeight = 700
                    boxShadow = 'inset 0 0 0 1px rgba(81,200,207,0.4)'
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
                    <td
                      key={j}
                      className="px-3 py-1.5 text-center tabular-nums"
                      style={{
                        background: bg,
                        color,
                        fontWeight,
                        fontStyle,
                        boxShadow,
                        borderBottom: '1px solid var(--ij-border)',
                      }}
                    >
                      {formatNum(val)}
                    </td>
                  )
                })}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
