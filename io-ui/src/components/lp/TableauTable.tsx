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
      <table className="min-w-full text-sm border-collapse font-mono">
        <thead>
          <tr>
            <th className="px-3 py-2 text-left text-slate-500 font-normal border-b border-slate-200">
              base
            </th>
            {encabezados.map((h, j) => (
              <th
                key={j}
                className={cn(
                  'px-3 py-2 text-center font-semibold border-b border-slate-200',
                  columnaEntrada === j
                    ? 'bg-blue-100 border-b-2 border-b-blue-500 text-blue-700'
                    : 'text-slate-700'
                )}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {tableau.map((fila, i) => {
            const isZRow = i === m
            const isFilaSalida = !isZRow && filaSalida === i
            return (
              <tr key={i} className={cn(isZRow && 'bg-slate-50')}>
                <td
                  className={cn(
                    'px-3 py-1.5 text-slate-500 font-medium border-r border-slate-200',
                    isFilaSalida && 'border-l-2 border-l-yellow-400'
                  )}
                >
                  {isZRow ? 'z' : base[i]}
                </td>
                {fila.map((val, j) => {
                  const isPivote =
                    celdaPivote !== null && celdaPivote[0] === i && celdaPivote[1] === j
                  const isEntrada = columnaEntrada === j
                  return (
                    <td
                      key={j}
                      className={cn(
                        'px-3 py-1.5 text-center tabular-nums',
                        isPivote && 'bg-orange-200 font-bold text-orange-900',
                        !isPivote && isFilaSalida && 'bg-yellow-50',
                        !isPivote && !isFilaSalida && isEntrada && 'bg-blue-50',
                        isZRow && !isPivote && 'italic text-slate-600'
                      )}
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
