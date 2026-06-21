import { useState, useMemo } from 'react'
import type { SolveStep, TableauHighlights } from '@/types/io'

export function useTableauNavigation(steps: SolveStep[]) {
  const [index, setIndex] = useState(0)

  const pasoActual = steps[index] ?? steps[0]
  const totalPasos = steps.length

  const highlights = useMemo<TableauHighlights>(() => {
    if (!pasoActual) return { columnaEntrada: null, filaSalida: null, celdaPivote: null }
    const { encabezados, base, varEntra, varSale } = pasoActual.datos
    const columnaEntrada = varEntra != null ? encabezados.indexOf(varEntra) : null
    const filaSalida = varSale != null ? base.indexOf(varSale) : null
    const celdaPivote =
      filaSalida !== null && filaSalida >= 0 && columnaEntrada !== null && columnaEntrada >= 0
        ? ([filaSalida, columnaEntrada] as [number, number])
        : null
    return { columnaEntrada, filaSalida, celdaPivote }
  }, [pasoActual])

  return {
    pasoActual,
    numeroPaso: index + 1,
    totalPasos,
    puedeAnterior: index > 0,
    puedeSiguiente: index < totalPasos - 1,
    irAnterior: () => setIndex(i => Math.max(0, i - 1)),
    irSiguiente: () => setIndex(i => Math.min(totalPasos - 1, i + 1)),
    highlights,
    resetIndex: () => setIndex(0),
  }
}
