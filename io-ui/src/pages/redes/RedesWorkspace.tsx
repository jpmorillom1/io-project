import { useState } from 'react'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { RedModelEditor } from '@/components/redes/RedModelEditor'
import { RedResultViewer } from '@/components/redes/RedResultViewer'
import { RedGrafo } from '@/components/redes/RedGrafo'
import { Separator } from '@/components/ui/separator'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Network, ChevronDown } from 'lucide-react'

export function RedesWorkspace() {
  const resultadoRed = useWorkspaceStore(s => s.resultadoRed)
  const status = useWorkspaceStore(s => s.status)
  const [flujoBExpanded, setFlujoBExpanded] = useState(false)

  const isIdle = status === 'IDLE'

  return (
    <div className="flex h-full overflow-hidden gap-3">
      {/* Panel izquierdo — Asistente Pivot (card flotante) */}
      <div
        className="w-[420px] shrink-0 flex flex-col overflow-hidden rounded-[10px]"
        style={{ background: 'var(--ij-bg-editor)', boxShadow: '0 0 0 1px var(--ij-bg-editor)' }}
      >
        <ChatPanel />
      </div>

      {/* Panel derecho */}
      <div className="flex-1 overflow-y-auto">
        {isIdle ? (
          <div className="flex flex-col items-center justify-center h-full p-12 text-center gap-4">
            <div
              className="h-12 w-12 rounded-[4px] flex items-center justify-center"
              style={{ background: 'rgba(20,196,182,0.12)' }}
            >
              <Network className="h-6 w-6" style={{ color: 'var(--ij-teal)' }} />
            </div>
            <div>
              <h2 className="text-base font-semibold" style={{ color: 'var(--ij-text-primary)' }}>
                Cuéntale tu problema de redes al Asistente Pivot
              </h2>
              <p className="text-sm mt-1 max-w-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                Describe los nodos y conexiones de tu red; el tutor la formulará y la resolverá
                paso a paso (Dijkstra, Kruskal, flujo máximo, flujo de costo mínimo o asignación).
              </p>
            </div>

            <div className="mt-4 w-full max-w-xl">
              <button
                onClick={() => setFlujoBExpanded(v => !v)}
                className="flex items-center gap-1.5 mx-auto transition-colors duration-[120ms]"
                style={{ fontSize: '12px', color: 'var(--ij-text-secondary)', background: 'none', border: 'none', cursor: 'pointer' }}
                onMouseEnter={e => (e.currentTarget.style.color = 'var(--ij-text-primary)')}
                onMouseLeave={e => (e.currentTarget.style.color = 'var(--ij-text-secondary)')}
              >
                <ChevronDown className={`h-3.5 w-3.5 transition-transform ${flujoBExpanded ? 'rotate-180' : ''}`} />
                Ingresar el grafo directamente (sin chat)
              </button>
              {flujoBExpanded && (
                <div className="mt-4">
                  <Card>
                    <CardContent className="pt-4">
                      <RedModelEditor />
                    </CardContent>
                  </Card>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="pt-0 pr-4 pb-6 max-w-4xl space-y-5">
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle>Problema de redes</CardTitle>
                  <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                    Completado por Pivot o editado manualmente
                  </span>
                </div>
              </CardHeader>
              <CardContent>
                <RedModelEditor />
              </CardContent>
            </Card>

            <RedGrafo />

            {resultadoRed && (
              <>
                <Separator />
                <RedResultViewer resultado={resultadoRed} />
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
