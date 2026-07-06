import { useState, useEffect } from 'react'
import { RedResultBanner } from './RedResultBanner'
import { NetworkGraph } from '@/components/shared/NetworkGraph'
import { grafoDePaso, esDirigido, esCurvado } from '@/lib/redes/construirGrafo'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { formatNum } from '@/lib/utils'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { SolveResultRed } from '@/types/io'

interface Props {
  resultado: SolveResultRed
}

/**
 * Visor paso a paso de un problema de redes. Espeja TransporteResultViewer:
 * una Card con navegación entre pasos, título/descripción del paso, el grafo
 * de la iteración (con los estados que calcula el backend) y, en el último
 * paso, el banner de resultado.
 */
export function RedResultViewer({ resultado }: Props) {
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
  if (d.nodoActual != null) meta.push(`Nodo actual = ${d.nodoActual}`)
  if (d.aristaEvaluada != null) meta.push(`Arista evaluada = ${d.aristaEvaluada.replace('->', ' → ')}`)
  if (d.pesoAcumulado != null) meta.push(`Peso acumulado = ${formatNum(d.pesoAcumulado)}`)
  if (d.camino != null && d.camino.length > 0) meta.push(`Camino: ${d.camino.join(' → ')}`)
  if (d.cuelloBotella != null) meta.push(`Cuello de botella = ${formatNum(d.cuelloBotella)}`)
  if (d.flujoTotal != null) meta.push(`Flujo total = ${formatNum(d.flujoTotal)}`)
  if (d.costoUnitario != null) meta.push(`Costo unitario del camino = ${formatNum(d.costoUnitario)}`)
  if (d.costoAcumulado != null) meta.push(`Costo acumulado = ${formatNum(d.costoAcumulado)}`)
  if (d.distancia != null) meta.push(`Distancia = ${formatNum(d.distancia)}`)
  if (d.pesoTotal != null) meta.push(`Peso total = ${formatNum(d.pesoTotal)}`)
  if (d.costoTotal != null) meta.push(`Costo total = ${formatNum(d.costoTotal)}`)

  const grafo = grafoDePaso(d)

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>③ Resolución paso a paso</CardTitle>
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
        <NetworkGraph
          nodes={grafo.nodes}
          edges={grafo.edges}
          directed={esDirigido(d.metodo)}
          curved={esCurvado(d.metodo)}
        />
        {esUltimo && (
          <div className="mt-4">
            <RedResultBanner resultado={resultado} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
