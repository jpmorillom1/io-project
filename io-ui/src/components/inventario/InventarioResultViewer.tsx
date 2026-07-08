import { type CSSProperties } from 'react'
import { InventarioResultCard } from './InventarioResultCard'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { formatNum } from '@/lib/utils'
import type { SolveResultInventario, SolveStepInventario } from '@/types/io'

interface Props {
  resultado: SolveResultInventario
}

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace" }

/**
 * Visor de Inventarios. A diferencia de los demás módulos no hay iteraciones que
 * navegar: los `steps` son las etapas del cálculo (parámetros → fórmula →
 * sustitución → resultado). Se listan en vertical y debajo va la tarjeta de
 * resultado con la política óptima.
 */
export function InventarioResultViewer({ resultado }: Props) {
  const { steps } = resultado
  if (steps.length === 0) return null

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle>③ Desarrollo paso a paso</CardTitle>
        <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
          Cada paso desarrolla una fórmula del modelo: parámetros, sustitución y resultado.
        </p>
      </CardHeader>
      <CardContent className="pt-2 space-y-3">
        <ol className="space-y-3">
          {steps.map(paso => (
            <PasoItem key={paso.numero} paso={paso} />
          ))}
        </ol>

        <Separator />
        <InventarioResultCard resultado={resultado} />
      </CardContent>
    </Card>
  )
}

function PasoItem({ paso }: { paso: SolveStepInventario }) {
  const { formula, sustitucion, resultado } = paso.datos
  const tieneCalculo = formula != null || sustitucion != null || resultado != null

  return (
    <li className="flex gap-3">
      <span
        className="shrink-0 h-6 w-6 rounded-full flex items-center justify-center text-xs font-semibold"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color: 'var(--ij-teal)', border: '1px solid var(--ij-border)' }}
      >
        {paso.numero}
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold" style={{ color: 'var(--ij-text-primary)' }}>{paso.titulo}</p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--ij-text-secondary)', lineHeight: 1.5 }}>
          {paso.descripcion}
        </p>
        {tieneCalculo && (
          <div
            className="mt-1.5 rounded-[4px] px-3 py-2 overflow-x-auto flex items-center gap-2 flex-wrap"
            style={{ background: 'var(--ij-bg-secondary)', border: '1px solid var(--ij-border)', ...MONO, fontSize: '12px' }}
          >
            {formula != null && <span style={{ color: 'var(--ij-purple)' }}>{formula}</span>}
            {sustitucion != null && (
              <>
                <span style={{ color: 'var(--ij-text-muted)' }}>=</span>
                <span style={{ color: 'var(--ij-text-primary)' }}>{sustitucion}</span>
              </>
            )}
            {resultado != null && (
              <>
                <span style={{ color: 'var(--ij-text-muted)' }}>=</span>
                <span style={{ color: 'var(--ij-green)', fontWeight: 700 }}>{formatNum(resultado)}</span>
              </>
            )}
          </div>
        )}
      </div>
    </li>
  )
}
