import type { GraphNode, GraphEdge } from '@/components/shared/NetworkGraph'
import type { ModeloTransporte, SolucionTransporte } from '@/types/io'
import { formatNum } from '@/lib/utils'

export interface Grafo {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

const ORIGEN_X = 0.06
const DESTINO_X = 0.94

/** Posición vertical normalizada del nodo idx de una columna de n nodos. */
function filaY(idx: number, n: number): number {
  return n === 1 ? 0.5 : idx / (n - 1)
}

function nodosBipartitos(
  origenes: string[],
  destinos: string[],
  subOrigen: (i: number) => string | undefined,
  subDestino: (j: number) => string | undefined,
): GraphNode[] {
  const nodes: GraphNode[] = []
  origenes.forEach((o, i) =>
    nodes.push({
      id: `O${i}`,
      label: o,
      sublabel: subOrigen(i),
      x: ORIGEN_X,
      y: filaY(i, origenes.length),
      variant: o === 'Ficticio' ? 'default' : 'origen',
    }),
  )
  destinos.forEach((d, j) =>
    nodes.push({
      id: `D${j}`,
      label: d,
      sublabel: subDestino(j),
      x: DESTINO_X,
      y: filaY(j, destinos.length),
      variant: d === 'Ficticio' ? 'default' : 'destino',
    }),
  )
  return nodes
}

/** Grafo del MODELO: todas las rutas posibles, etiquetadas con su costo (tenue). */
export function grafoModeloTransporte(modelo: ModeloTransporte): Grafo {
  const nodes = nodosBipartitos(
    modelo.origenes,
    modelo.destinos,
    i => `oferta ${formatNum(modelo.oferta[i] ?? 0)}`,
    j => `demanda ${formatNum(modelo.demanda[j] ?? 0)}`,
  )

  const edges: GraphEdge[] = []
  modelo.origenes.forEach((_, i) =>
    modelo.destinos.forEach((_, j) => {
      edges.push({
        from: `O${i}`,
        to: `D${j}`,
        label: formatNum(modelo.costos[i]?.[j] ?? 0),
        active: false,
      })
    }),
  )
  return { nodes, edges }
}

/** Grafo de la RESOLUCIÓN: solo las rutas usadas (flujo > 0), destacadas y con el flujo. */
export function grafoSolucionTransporte(sol: SolucionTransporte): Grafo {
  const sumaFila = (i: number) => sol.asignaciones[i]?.reduce((a, b) => a + b, 0) ?? 0
  const sumaCol = (j: number) =>
    sol.asignaciones.reduce((a, fila) => a + (fila[j] ?? 0), 0)

  const nodes = nodosBipartitos(
    sol.origenes,
    sol.destinos,
    i => `envía ${formatNum(sumaFila(i))}`,
    j => `recibe ${formatNum(sumaCol(j))}`,
  )

  const edges: GraphEdge[] = []
  sol.asignaciones.forEach((fila, i) =>
    fila.forEach((cant, j) => {
      if (cant > 1e-9) {
        edges.push({
          from: `O${i}`,
          to: `D${j}`,
          label: formatNum(cant),
          value: cant,
          active: true,
        })
      }
    }),
  )
  return { nodes, edges }
}
