import { useState, useEffect } from 'react'
import { EnteraResultBanner } from './EnteraResultBanner'
import { EnteraTree, EnteraTreeLegend } from './EnteraTree'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { formatNum } from '@/lib/utils'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { SolveResultEntera, AccionNodo } from '@/types/io'

interface Props {
  resultado: SolveResultEntera
}

const ACCION_LABEL: Record<AccionNodo, string> = {
  RAMIFICA: 'Ramifica',
  INCUMBENTE: 'Nuevo incumbente',
  PODA_COTA: 'Poda por cota',
  PODA_INFACTIBLE: 'Poda por infactibilidad',
}

/**
 * Visor paso a paso de Branch & Bound. Espeja RedResultViewer: Card con
 * navegación entre pasos; cada paso resalta su nodo en el árbol y muestra la
 * relajación resuelta. El último paso lleva el banner de resultado.
 */
export function EnteraResultViewer({ resultado }: Props) {
  const { steps } = resultado
  const [index, setIndex] = useState(steps.length - 1)

  useEffect(() => {
    setIndex(resultado.steps.length - 1)
  }, [resultado])

  if (steps.length === 0) return null

  const idx = Math.min(index, steps.length - 1)
  const paso = steps[idx]
  const puedeAnterior = idx > 0
  const puedeSiguiente = idx < steps.length - 1
  const esUltimo = idx === steps.length - 1

  const d = paso.datos
  const meta: string[] = []
  if (d.rama != null) meta.push(`Rama: ${d.rama}`)
  if (d.zRelajacion != null) meta.push(`z relajado = ${formatNum(d.zRelajacion)}`)
  if (d.valoresRelajacion != null) {
    const vals = Object.entries(d.valoresRelajacion).map(([k, v]) => `${k}=${formatNum(v)}`).join(', ')
    if (vals) meta.push(`Relajación: ${vals}`)
  }
  if (d.accion != null) meta.push(ACCION_LABEL[d.accion])
  if (d.varRamificada != null && d.valorFraccionario != null) {
    meta.push(`Ramifica en ${d.varRamificada} = ${formatNum(d.valorFraccionario)}`)
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>③ Branch &amp; Bound paso a paso</CardTitle>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => setIndex(i => i - 1)} disabled={!puedeAnterior}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span
              className="text-sm min-w-[80px] text-center"
              style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-text-secondary)' }}
            >
              {idx + 1} / {steps.length}
            </span>
            <Button variant="outline" size="icon" onClick={() => setIndex(i => i + 1)} disabled={!puedeSiguiente}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
        <p className="text-sm font-semibold mt-1" style={{ color: 'var(--ij-text-primary)' }}>
          {paso.titulo}
        </p>
        <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
          {paso.descripcion}
        </p>
      </CardHeader>

      {meta.length > 0 && (
        <>
          <div className="px-4 pb-2 flex gap-4 text-xs flex-wrap" style={{ color: 'var(--ij-text-secondary)' }}>
            {meta.map((m, i) => <span key={i}>{m}</span>)}
          </div>
          <Separator />
        </>
      )}

      <CardContent className="pt-4 space-y-3">
        <EnteraTree steps={steps} seleccionado={d.nodoId ?? null} />
        <EnteraTreeLegend />
        {esUltimo && (
          <div className="mt-4">
            <EnteraResultBanner resultado={resultado} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
