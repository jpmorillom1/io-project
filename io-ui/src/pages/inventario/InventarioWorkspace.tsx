import { useState } from 'react'
import { InventarioModelEditor } from '@/components/inventario/InventarioModelEditor'
import { InventarioResultViewer } from '@/components/inventario/InventarioResultViewer'
import { AnimatedGroup } from '@/components/motion-primitives/animated-group'
import { cardGroup } from '@/lib/motion'
import { Separator } from '@/components/ui/separator'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Package, ChevronDown } from 'lucide-react'

export function InventarioWorkspace() {
  const resultadoInventario = useWorkspaceStore(s => s.resultadoInventario)
  const status = useWorkspaceStore(s => s.status)
  const revisionIA = useWorkspaceStore(s => s.revisionIA)
  const [flujoBExpanded, setFlujoBExpanded] = useState(false)

  const isIdle = status === 'IDLE'

  return (
    // Solo el panel del módulo: el chat lo monta AppShell una única vez, por encima
    // de las rutas, para que no se remonte al cambiar de módulo.
    <div className="h-full overflow-y-auto">
        {isIdle ? (
          <div className="flex flex-col items-center justify-center h-full p-12 text-center gap-4">
            <div
              className="h-12 w-12 rounded-[4px] flex items-center justify-center"
              style={{ background: 'rgba(20,196,182,0.12)' }}
            >
              <Package className="h-6 w-6" style={{ color: 'var(--ij-teal)' }} />
            </div>
            <div>
              <h2 className="text-base font-semibold" style={{ color: 'var(--ij-text-primary)' }}>
                Cuéntale tu problema de inventarios al Asistente Pivot
              </h2>
              <p className="text-sm mt-1 max-w-md mx-auto" style={{ color: 'var(--ij-text-secondary)' }}>
                Describe la demanda y los costos de tu artículo; el tutor identificará el modelo
                (EOQ, con faltantes o descuentos, producción económica o punto de reorden) y lo
                resolverá desarrollando las fórmulas paso a paso.
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
                Ingresar los parámetros directamente (sin chat)
              </button>
              {flujoBExpanded && (
                <div className="mt-4">
                  <Card>
                    <CardContent className="pt-4">
                      <InventarioModelEditor />
                    </CardContent>
                  </Card>
                </div>
              )}
            </div>
          </div>
        ) : (
          // `key` = revisionIA: las cards se re-animan cuando Pivot entrega modelo o
          // resultado, no cuando el usuario edita los parámetros a mano.
          <AnimatedGroup
            key={revisionIA}
            className="pt-0 pr-4 pb-6 max-w-4xl space-y-5"
            variants={cardGroup}
          >
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle>Problema de inventarios</CardTitle>
                  <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                    Completado por Pivot o editado manualmente
                  </span>
                </div>
              </CardHeader>
              <CardContent>
                <InventarioModelEditor />
              </CardContent>
            </Card>

            {resultadoInventario && <Separator />}
            {resultadoInventario && <InventarioResultViewer resultado={resultadoInventario} />}
          </AnimatedGroup>
      )}
    </div>
  )
}
