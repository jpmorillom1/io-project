import { useState } from 'react'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { ProblemInput } from '@/components/lp/ProblemInput'
import { ModelEditor } from '@/components/lp/ModelEditor'
import { TableauViewer } from '@/components/lp/TableauViewer'
import { Separator } from '@/components/ui/separator'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { MessageSquareText, ChevronDown } from 'lucide-react'

export function SimplexWorkspace() {
  const resultado = useWorkspaceStore(s => s.resultado)
  const status = useWorkspaceStore(s => s.status)
  const [flujoBExpanded, setFlujoBExpanded] = useState(false)

  const isIdle = status === 'IDLE'

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: 'var(--ij-bg-secondary)' }}>
      {/* Panel izquierdo — Tutor Ío */}
      <div
        className="w-[420px] shrink-0 flex flex-col overflow-hidden"
        style={{
          background: 'var(--ij-bg-editor)',
          borderTopRightRadius: '14px',
          borderBottomRightRadius: '14px',
          boxShadow: '0 0 0 1px var(--ij-bg-editor)',
        }}
      >
        <ChatPanel />
      </div>

      {/* Panel derecho */}
      <div className="flex-1 overflow-y-auto" style={{ background: 'var(--ij-bg-secondary)' }}>
        {isIdle ? (
          <div className="flex flex-col items-center justify-center h-full p-12 text-center gap-4">
            <div
              className="h-12 w-12 rounded-[4px] flex items-center justify-center"
              style={{ background: 'rgba(20,196,182,0.12)' }}
            >
              <MessageSquareText className="h-6 w-6" style={{ color: 'var(--ij-teal)' }} />
            </div>
            <div>
              <h2
                className="text-base font-semibold"
                style={{ color: 'var(--ij-text-primary)' }}
              >
                Cuéntale tu problema al Tutor Ío
              </h2>
              <p className="text-sm mt-1 max-w-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                Escribe en el chat y el tutor formulará el modelo, lo validará y lo resolverá paso a paso.
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
                <ChevronDown
                  className={`h-3.5 w-3.5 transition-transform ${flujoBExpanded ? 'rotate-180' : ''}`}
                />
                Ingresar modelo directamente (sin chat)
              </button>
              {flujoBExpanded && (
                <div className="mt-4 space-y-4">
                  <Card>
                    <CardContent className="pt-4">
                      <ProblemInput />
                    </CardContent>
                  </Card>
                  <Card>
                    <CardContent className="pt-4">
                      <ModelEditor />
                    </CardContent>
                  </Card>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="p-6 space-y-5">
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle>Modelo LP</CardTitle>
                  <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                    Completado por Ío o editado manualmente
                  </span>
                </div>
              </CardHeader>
              <CardContent>
                <ModelEditor />
              </CardContent>
            </Card>

            <details className="group">
              <summary
                className="flex items-center gap-1.5 cursor-pointer list-none select-none w-fit transition-colors duration-[120ms]"
                style={{ fontSize: '12px', color: 'var(--ij-text-secondary)' }}
              >
                <ChevronDown className="h-3 w-3 group-open:rotate-180 transition-transform" />
                Sugerir modelo desde texto (alternativa sin chat)
              </summary>
              <div className="mt-3 ml-4">
                <ProblemInput />
              </div>
            </details>

            {resultado && (
              <>
                <Separator />
                <TableauViewer resultado={resultado} />
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
