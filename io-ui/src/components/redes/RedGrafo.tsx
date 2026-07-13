import { useState, useEffect } from 'react'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { NetworkGraph } from '@/components/shared/NetworkGraph'
import { grafoModeloRed, grafoDePaso, esDirigido, esCurvado } from '@/lib/redes/construirGrafo'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'

type Vista = 'modelo' | 'resolucion'

/**
 * Grafo del problema de redes. Muestra el MODELO (todas las aristas, tenues) y,
 * cuando hay resolución, el grafo del último paso con la solución destacada.
 * El toggle permite volver a ver el modelo para comparar (espejo de TransporteGrafo).
 */
export function RedGrafo() {
  const resultado = useWorkspaceStore(s => s.resultadoRed)
  const modeloGrafico = useWorkspaceStore(s => s.modeloRedGrafico)
  const modeloResuelto = useWorkspaceStore(s => s.modeloRed)

  const modelo = modeloGrafico ?? modeloResuelto
  const ultimoPaso = resultado ? resultado.steps[resultado.steps.length - 1] ?? null : null
  const hayModelo = !!modelo
  const haySolucion = !!ultimoPaso

  const [vista, setVista] = useState<Vista>('resolucion')

  // Al llegar una resolución, salta a mostrarla; si no hay, cae al modelo.
  useEffect(() => {
    setVista(haySolucion ? 'resolucion' : 'modelo')
  }, [haySolucion])

  if (!hayModelo && !haySolucion) return null

  const vistaEfectiva: Vista = vista === 'resolucion' && haySolucion ? 'resolucion' : 'modelo'
  const grafo =
    vistaEfectiva === 'resolucion' && ultimoPaso
      ? grafoDePaso(ultimoPaso.datos)
      : modelo
        ? grafoModeloRed(modelo)
        : null
  const metodo = vistaEfectiva === 'resolucion' && ultimoPaso ? ultimoPaso.datos.metodo : modelo?.metodo

  if (!grafo || !metodo) return null

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>Red del problema</CardTitle>
          {hayModelo && haySolucion && (
            <div className="flex rounded-[4px] overflow-hidden" style={{ border: '1px solid var(--ij-border)' }}>
              <Toggle activo={vistaEfectiva === 'modelo'} onClick={() => setVista('modelo')}>Modelo</Toggle>
              <Toggle activo={vistaEfectiva === 'resolucion'} onClick={() => setVista('resolucion')}>Resolución</Toggle>
            </div>
          )}
        </div>
        <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
          {vistaEfectiva === 'resolucion'
            ? 'Aristas de la solución destacadas en verde.'
            : 'Todas las aristas del modelo con su peso / capacidad / costo.'}
        </p>
      </CardHeader>
      <CardContent className="pt-2">
        <NetworkGraph
          nodes={grafo.nodes}
          edges={grafo.edges}
          directed={esDirigido(metodo)}
          curved={esCurvado(metodo)}
        />
        <div className="mt-1 flex gap-4 flex-wrap" style={{ fontSize: 11, color: 'var(--ij-text-secondary)' }}>
          <Leyenda color="#fb923c" texto="Fuente" />
          <Leyenda color="#f87171" texto="Sumidero" />
          <Leyenda color="#34d399" texto="Solución / camino activo" />
          <Leyenda color="var(--ij-text-muted)" texto="Arista sin usar" />
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
