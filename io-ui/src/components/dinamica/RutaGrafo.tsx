import { NetworkGraph, type GraphNode, type GraphEdge, type ColumnLabel } from '@/components/shared/NetworkGraph'
import { formatNum } from '@/lib/utils'
import type { EtapaRuta, ArcoRuta } from '@/types/io'

interface Props {
  etapas: EtapaRuta[]
  arcos: ArcoRuta[]
  rutaOptima: string[] | null
}

/** ¿El par (a→b) son consecutivos dentro de la ruta óptima? */
function enRuta(ruta: string[] | null, a: string, b: string): boolean {
  if (!ruta) return false
  for (let i = 0; i < ruta.length - 1; i++) {
    if (ruta[i] === a && ruta[i + 1] === b) return true
  }
  return false
}

/**
 * Grafo de la red por etapas (solo RUTA_ETAPAS). Reusa NetworkGraph: cada etapa es
 * una columna, los nodos se reparten en su columna y la ruta óptima se resalta.
 */
export function RutaGrafo({ etapas, arcos, rutaOptima }: Props) {
  const nEtapas = etapas.length
  if (nEtapas === 0) return null

  const enRutaNodo = new Set(rutaOptima ?? [])
  const nodes: GraphNode[] = []
  const columnLabels: ColumnLabel[] = []

  etapas.forEach((etapa, ei) => {
    const x = nEtapas > 1 ? ei / (nEtapas - 1) : 0.5
    columnLabels.push({ x, label: `Etapa ${etapa.etapa}` })
    const n = etapa.nodos.length
    etapa.nodos.forEach((nodo, j) => {
      const y = n > 1 ? j / (n - 1) : 0.5
      const esPrimera = ei === 0
      const esUltima = ei === nEtapas - 1
      nodes.push({
        id: nodo,
        label: nodo,
        x,
        y,
        variant: esPrimera ? 'source' : esUltima ? 'sink' : enRutaNodo.has(nodo) ? 'active' : 'default',
      })
    })
  })

  const edges: GraphEdge[] = arcos.map(a => ({
    from: a.origen,
    to: a.destino,
    label: formatNum(a.costo),
    active: enRuta(rutaOptima, a.origen, a.destino),
  }))

  return <NetworkGraph nodes={nodes} edges={edges} directed curved={false} columnLabels={columnLabels} />
}
