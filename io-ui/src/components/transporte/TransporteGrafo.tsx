import { useState, useEffect } from 'react'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { NetworkGraph } from '@/components/shared/NetworkGraph'
import { grafoModeloTransporte, grafoSolucionTransporte } from '@/lib/transporte/construirGrafo'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'

type Vista = 'modelo' | 'resolucion'

/**
 * Grafo de un problema de transporte: orígenes (izquierda) → destinos (derecha).
 * Muestra el MODELO (rutas posibles con su costo) y, cuando hay resolución, las
 * rutas usadas con el flujo. El mismo componente se actualiza al resolver; el
 * toggle permite volver a ver el modelo para comparar.
 */
export function TransporteGrafo() {
  const resultado = useWorkspaceStore(s => s.resultadoTransporte)
  const modeloGrafico = useWorkspaceStore(s => s.modeloTransporteGrafico)
  const modeloResuelto = useWorkspaceStore(s => s.modeloTransporte)

  const modelo = modeloGrafico ?? modeloResuelto
  const solucion = resultado?.solution ?? null
  const hayModelo = !!modelo
  const haySolucion = !!solucion

  const [vista, setVista] = useState<Vista>('resolucion')

  // Al llegar una resolución, salta a mostrarla; si no hay, cae al modelo.
  useEffect(() => {
    setVista(haySolucion ? 'resolucion' : 'modelo')
  }, [haySolucion])

  if (!hayModelo && !haySolucion) return null

  const vistaEfectiva: Vista = vista === 'resolucion' && haySolucion ? 'resolucion' : 'modelo'
  const grafo =
    vistaEfectiva === 'resolucion' && solucion
      ? grafoSolucionTransporte(solucion)
      : modelo
        ? grafoModeloTransporte(modelo)
        : null

  if (!grafo) return null

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>Red de transporte</CardTitle>
          {hayModelo && haySolucion && (
            <div className="flex rounded-[4px] overflow-hidden" style={{ border: '1px solid var(--ij-border)' }}>
              <Toggle activo={vistaEfectiva === 'modelo'} onClick={() => setVista('modelo')}>Modelo</Toggle>
              <Toggle activo={vistaEfectiva === 'resolucion'} onClick={() => setVista('resolucion')}>Resolución</Toggle>
            </div>
          )}
        </div>
        <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
          {vistaEfectiva === 'resolucion'
            ? 'Rutas utilizadas y unidades enviadas por cada una.'
            : 'Orígenes → destinos con el costo unitario de cada ruta.'}
        </p>
      </CardHeader>
      <CardContent className="pt-2">
        <NetworkGraph
          nodes={grafo.nodes}
          edges={grafo.edges}
          directed
          columnLabels={[
            { x: 0.06, label: 'Orígenes' },
            { x: 0.94, label: 'Destinos' },
          ]}
        />
        <div className="mt-1 flex gap-4 flex-wrap" style={{ fontSize: 11, color: 'var(--ij-text-secondary)' }}>
          <Leyenda color="#a78bfa" texto="Origen" />
          <Leyenda color="#22d3c4" texto="Destino" />
          {vistaEfectiva === 'resolucion'
            ? <Leyenda color="#34d399" texto="Ruta usada · nº = flujo" />
            : <Leyenda color="var(--ij-text-muted)" texto="Ruta posible · nº = costo" />}
        </div>
      </CardContent>
    </Card>
  )
}

function Toggle({ activo, onClick, children }: { activo: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className="px-2.5 py-1 transition-colors duration-[120ms]"
      style={{
        fontSize: 11,
        fontFamily: "'JetBrains Mono', monospace",
        background: activo ? 'rgba(20,196,182,0.14)' : 'transparent',
        color: activo ? 'var(--ij-teal)' : 'var(--ij-text-secondary)',
        border: 'none',
        cursor: 'pointer',
      }}
    >
      {children}
    </button>
  )
}

function Leyenda({ color, texto }: { color: string; texto: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: color }} />
      {texto}
    </span>
  )
}
