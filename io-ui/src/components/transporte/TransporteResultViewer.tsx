import { useState, useEffect } from 'react'
import { TransporteStepTable } from './TransporteStepTable'
import { TransporteResultBanner } from './TransporteResultBanner'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { formatNum } from '@/lib/utils'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { SolveResultTransporte } from '@/types/io'

interface Props {
  resultado: SolveResultTransporte
}

/**
 * Visor paso a paso de un problema de transporte. Espeja TableauViewer (LP):
 * una Card con navegación entre pasos, título/descripción del paso, la tabla de
 * la iteración y, en el último paso, el banner de resultado.
 */
export function TransporteResultViewer({ resultado }: Props) {
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

  const meta: string[] = []
  if (paso.datos.costoTotal != null) meta.push(`Costo actual = ${formatNum(paso.datos.costoTotal)}`)
  if (paso.datos.theta != null) meta.push(`θ reasignado = ${formatNum(paso.datos.theta)}`)
  if (paso.datos.celdasBasicas != null) meta.push(`Celdas básicas = ${paso.datos.celdasBasicas}`)

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>③ Tabla de transporte paso a paso</CardTitle>
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

      <CardContent className="pt-4">
        <TransporteStepTable datos={paso.datos} />
        {esUltimo && (
          <div className="mt-4">
            <TransporteResultBanner resultado={resultado} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
