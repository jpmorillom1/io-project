import { useEffect, useState } from 'react'
import { GraficoChart } from './GraficoChart'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import type { SolveResultGrafico } from '@/types/io'
import { ChevronLeft, ChevronRight } from 'lucide-react'

interface Props {
  resultado: SolveResultGrafico
}

function ResultadoBanner({ resultado }: Props) {
  if (resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') {
    const sol = resultado.solution!
    return (
      <div
        className="rounded-[4px] p-3 space-y-2"
        style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
      >
        <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>
          {resultado.status === 'OPTIMO' ? 'Solución óptima' : 'Soluciones óptimas múltiples'}
        </p>
        <p style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 15, color: 'var(--ij-green)' }}>
          <span style={{ color: 'var(--ij-text-secondary)' }}>Z* = </span>
          {sol.valorOptimo}
        </p>
        <div className="flex gap-4 flex-wrap">
          {Object.entries(sol.valores).map(([k, v]) => (
            <span key={k} style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
              <span style={{ color: 'var(--ij-purple)' }}>{k}</span>
              <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
              <span style={{ color: 'var(--ij-green)' }}>{v}</span>
            </span>
          ))}
        </div>
      </div>
    )
  }

  const isNoAcotado = resultado.status === 'NO_ACOTADO'
  const color = isNoAcotado ? 'var(--ij-amber)' : 'var(--ij-red)'
  const msgs: Record<string, string> = {
    INFACTIBLE: 'No existe solución que satisfaga todas las restricciones.',
    NO_ACOTADO: 'La función objetivo puede crecer indefinidamente.',
    ERROR: 'Error interno al procesar el modelo.',
  }
  return (
    <div
      className="rounded-[4px] p-3"
      style={{
        background: isNoAcotado ? 'rgba(255,200,89,0.08)' : 'rgba(255,82,99,0.08)',
        borderLeft: `2px solid ${color}`,
      }}
    >
      <p className="font-semibold text-sm" style={{ color }}>{resultado.status}</p>
      <p className="text-sm mt-1" style={{ color, opacity: 0.85 }}>
        {msgs[resultado.status] ?? 'Estado desconocido'}
      </p>
    </div>
  )
}

export function GraficoResultViewer({ resultado }: Props) {
  const steps = resultado.steps
  const [stepIdx, setStepIdx] = useState(steps.length - 1)

  useEffect(() => {
    setStepIdx(steps.length - 1)
  }, [resultado])

  const pasoActual = steps[stepIdx] ?? null
  const puedeAnterior = stepIdx > 0
  const puedeSiguiente = stepIdx < steps.length - 1

  return (
    <div className="space-y-4">
      <ResultadoBanner resultado={resultado} />

      {resultado.solution && resultado.solution.vertices.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle>Vértices de la región factible</CardTitle>
          </CardHeader>
          <CardContent className="pt-2">
            <table style={{ borderCollapse: 'collapse', width: '100%' }}>
              <thead>
                <tr>
                  {['Punto', 'Z'].map(h => (
                    <th key={h} style={{
                      textAlign: 'left', fontSize: 10, color: 'var(--ij-text-secondary)',
                      fontFamily: "'JetBrains Mono', monospace", paddingBottom: 4, fontWeight: 600,
                    }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {resultado.solution.vertices.map((v, i) => (
                  <tr key={i}>
                    <td style={{
                      fontFamily: "'JetBrains Mono', monospace", fontSize: 11,
                      color: v.esOptimo ? 'var(--ij-teal)' : 'var(--ij-text-primary)',
                      paddingTop: 2, paddingBottom: 2,
                    }}>
                      {v.etiqueta}
                      {v.esOptimo && <span style={{ marginLeft: 4, fontSize: 9, color: 'var(--ij-teal)' }}>★</span>}
                    </td>
                    <td style={{
                      fontFamily: "'JetBrains Mono', monospace", fontSize: 11,
                      color: v.esOptimo ? 'var(--ij-green)' : 'var(--ij-text-secondary)',
                      paddingLeft: 12,
                    }}>
                      {v.valorZ}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle>Gráfico paso a paso</CardTitle>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="icon"
                onClick={() => setStepIdx(i => i - 1)}
                disabled={!puedeAnterior}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span
                className="text-sm min-w-[80px] text-center"
                style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-text-secondary)' }}
              >
                {stepIdx + 1} / {steps.length}
              </span>
              <Button
                variant="outline"
                size="icon"
                onClick={() => setStepIdx(i => i + 1)}
                disabled={!puedeSiguiente}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {pasoActual && (
            <>
              <p className="text-sm font-semibold mt-1" style={{ color: 'var(--ij-text-primary)' }}>
                {pasoActual.titulo}
              </p>
              <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                {pasoActual.descripcion}
              </p>
            </>
          )}
        </CardHeader>

        <CardContent className="pt-2">
          {pasoActual && <GraficoChart datos={pasoActual.datos} />}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-4 pb-3">
          <p className="text-xs font-semibold uppercase tracking-wider mb-2"
            style={{ color: 'var(--ij-text-secondary)' }}>
            Pasos
          </p>
          <div className="space-y-0.5">
            {steps.map((s, i) => (
              <button
                key={i}
                onClick={() => setStepIdx(i)}
                className="w-full text-left rounded-[4px] px-2 py-1 transition-colors duration-[80ms]"
                style={{
                  fontSize: 12,
                  fontFamily: "'JetBrains Mono', monospace",
                  background: i === stepIdx ? 'rgba(20,196,182,0.1)' : 'transparent',
                  color: i === stepIdx ? 'var(--ij-teal)' : 'var(--ij-text-secondary)',
                  border: 'none',
                  cursor: 'pointer',
                }}
              >
                <span style={{ opacity: 0.5 }}>{i}.</span> {s.titulo}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
