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
    <div className="flex h-screen overflow-hidden bg-slate-50">
      {/* Panel izquierdo — Tutor Ío (entrypoint principal, ancho prominente) */}
      <div className="w-[420px] shrink-0 flex flex-col shadow-sm">
        <ChatPanel />
      </div>

      {/* Panel derecho — Vista reactiva al chat */}
      <div className="flex-1 overflow-y-auto">
        {isIdle ? (
          /* Estado vacío: orienta al chat */
          <div className="flex flex-col items-center justify-center h-full p-12 text-center gap-4">
            <div className="h-12 w-12 rounded-2xl bg-blue-100 flex items-center justify-center">
              <MessageSquareText className="h-6 w-6 text-blue-500" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-slate-700">
                Cuéntale tu problema al Tutor Ío
              </h2>
              <p className="text-sm text-slate-400 mt-1 max-w-xs">
                Escribe en el chat y el tutor formulará el modelo, lo validará y lo resolverá paso a paso.
              </p>
            </div>

            {/* Flujo B — acceso alternativo sin chat */}
            <div className="mt-4 w-full max-w-xl">
              <button
                onClick={() => setFlujoBExpanded(v => !v)}
                className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-600 transition-colors mx-auto"
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
          /* Estado activo: formulario y tableau responden al chat */
          <div className="p-6 space-y-5">
            {/* Modelo LP — llenado por el chat o editado manualmente */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle>Modelo LP</CardTitle>
                  <span className="text-xs text-slate-400">Completado por Ío o editado manualmente</span>
                </div>
              </CardHeader>
              <CardContent>
                <ModelEditor />
              </CardContent>
            </Card>

            {/* Flujo B — sugerir modelo desde texto (acceso secundario) */}
            <details className="group">
              <summary className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-600 cursor-pointer list-none select-none w-fit">
                <ChevronDown className="h-3 w-3 group-open:rotate-180 transition-transform" />
                Sugerir modelo desde texto (alternativa sin chat)
              </summary>
              <div className="mt-3 ml-4">
                <ProblemInput />
              </div>
            </details>

            {/* Zona ③ — Tableau (aparece cuando el chat o el botón resuelven) */}
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
