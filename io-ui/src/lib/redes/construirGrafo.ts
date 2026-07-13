import type { GraphNode, GraphEdge, NodeVariant } from '@/components/shared/NetworkGraph'
import type { ModeloRed, MetodoRed, Arista, AristaPaso, StepDatosRed } from '@/types/io'
import { formatNum } from '@/lib/utils'

export interface Grafo {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

const IZQ_X = 0.06
const DER_X = 0.94

/** Posición vertical normalizada del nodo idx de una columna de n nodos. */
function filaY(idx: number, n: number): number {
  return n === 1 ? 0.5 : idx / (n - 1)
}

/** ¿El método se dibuja con flechas? Kruskal siempre es no dirigido. */
export function esDirigido(metodo: MetodoRed): boolean {
  return metodo !== 'KRUSKAL'
}

/** ¿Aristas curvas (estilo flujo)? Dijkstra/Kruskal van con rectas. */
export function esCurvado(metodo: MetodoRed): boolean {
  return metodo === 'EDMONDS_KARP' || metodo === 'FLUJO_COSTO_MINIMO' || metodo === 'ASIGNACION'
}

// ─── layouts ─────────────────────────────────────────────────────────────────

type Pos = { x: number; y: number }

/** Layout por defecto: nodos en círculo (sirve para cualquier grafo). */
function layoutCirculo(nodos: string[]): Map<string, Pos> {
  const pos = new Map<string, Pos>()
  const n = Math.max(1, nodos.length)
  nodos.forEach((nodo, i) => {
    const theta = (2 * Math.PI * i) / n - Math.PI / 2
    pos.set(nodo, { x: 0.5 + 0.4 * Math.cos(theta), y: 0.5 + 0.4 * Math.sin(theta) })
  })
  return pos
}

/**
 * Layout por capas BFS desde la fuente (recomendado para EK/MCF/Asignación):
 * capa = distancia BFS; la fuente queda a la izquierda y el sumidero a la derecha.
 * Los nodos inalcanzables van a una columna extra al final.
 */
function layoutCapas(nodos: string[], aristas: AristaPaso[] | Arista[], fuente: string): Map<string, Pos> {
  const ady = new Map<string, string[]>()
  nodos.forEach(n => ady.set(n, []))
  for (const a of aristas) {
    ady.get(a.origen)?.push(a.destino)
  }

  const capa = new Map<string, number>()
  capa.set(fuente, 0)
  const cola = [fuente]
  while (cola.length > 0) {
    const u = cola.shift()!
    for (const v of ady.get(u) ?? []) {
      if (!capa.has(v)) {
        capa.set(v, capa.get(u)! + 1)
        cola.push(v)
      }
    }
  }

  let maxCapa = 0
  capa.forEach(c => { maxCapa = Math.max(maxCapa, c) })
  // Inalcanzables: columna extra a la derecha.
  const hayInalcanzables = nodos.some(n => !capa.has(n))
  if (hayInalcanzables) maxCapa += 1
  nodos.forEach(n => { if (!capa.has(n)) capa.set(n, maxCapa) })

  // Índice vertical dentro de cada capa (en el orden de `nodos`).
  const porCapa = new Map<number, string[]>()
  nodos.forEach(n => {
    const c = capa.get(n)!
    if (!porCapa.has(c)) porCapa.set(c, [])
    porCapa.get(c)!.push(n)
  })

  const pos = new Map<string, Pos>()
  nodos.forEach(n => {
    const c = capa.get(n)!
    const grupo = porCapa.get(c)!
    const x = maxCapa === 0 ? 0.5 : IZQ_X + ((DER_X - IZQ_X) * c) / maxCapa
    pos.set(n, { x, y: filaY(grupo.indexOf(n), grupo.length) })
  })
  return pos
}

/** Elige el layout según el método y la presencia de fuente. */
function layout(metodo: MetodoRed, nodos: string[], aristas: AristaPaso[] | Arista[], fuente?: string | null): Map<string, Pos> {
  const conFuente = fuente && nodos.includes(fuente)
  if (esCurvado(metodo) && conFuente) return layoutCapas(nodos, aristas, fuente)
  return layoutCirculo(nodos)
}

// ─── etiquetas de arista ─────────────────────────────────────────────────────

/** Etiqueta de una arista del MODELO (sin flujo aún). */
function labelModelo(a: Arista, metodo: MetodoRed): string {
  switch (metodo) {
    case 'DIJKSTRA':
    case 'KRUSKAL':
      return formatNum(a.peso ?? 0)
    case 'EDMONDS_KARP':
      return formatNum(a.capacidad ?? 0)
    case 'FLUJO_COSTO_MINIMO':
      return `${formatNum(a.capacidad ?? 0)} ($${formatNum(a.costo ?? 0)})`
    default:
      return ''
  }
}

/** Etiqueta de una arista de un PASO (puede traer flujo). */
function labelPaso(a: AristaPaso, metodo: MetodoRed): string {
  switch (metodo) {
    case 'DIJKSTRA':
    case 'KRUSKAL':
      return formatNum(a.peso ?? 0)
    case 'EDMONDS_KARP':
      return `${formatNum(a.flujo ?? 0)}/${formatNum(a.capacidad ?? 0)}`
    case 'FLUJO_COSTO_MINIMO':
      return `${formatNum(a.flujo ?? 0)}/${formatNum(a.capacidad ?? 0)} ($${formatNum(a.costo ?? 0)})`
    case 'ASIGNACION':
      // Solo el costo del par agente→tarea; los arcos internos S→agente y
      // tarea→T (costo 0) van sin etiqueta para no saturar el dibujo.
      return a.costo != null && a.costo !== 0 ? `$${formatNum(a.costo)}` : ''
  }
}

// ─── grafos ──────────────────────────────────────────────────────────────────

/** Grafo del MODELO: todas las aristas tenues, etiquetadas según el método. */
export function grafoModeloRed(modelo: ModeloRed): Grafo {
  if (modelo.metodo === 'ASIGNACION') {
    return grafoModeloAsignacion(modelo)
  }

  const nodos = modelo.nodos ?? []
  const aristas = modelo.aristas ?? []
  const pos = layout(modelo.metodo, nodos, aristas, modelo.fuente)

  const nodes: GraphNode[] = nodos.map(n => ({
    id: n,
    label: n,
    x: pos.get(n)?.x ?? 0.5,
    y: pos.get(n)?.y ?? 0.5,
    variant: variantBase(n, modelo.fuente, modelo.sumidero, modelo.metodo),
  }))

  const edges: GraphEdge[] = aristas.map(a => ({
    from: a.origen,
    to: a.destino,
    label: labelModelo(a, modelo.metodo),
    active: false,
  }))

  return { nodes, edges }
}

/** Grafo bipartito del modelo de ASIGNACION: agentes → tareas con su costo. */
function grafoModeloAsignacion(modelo: ModeloRed): Grafo {
  const agentes = modelo.agentes ?? []
  const tareas = modelo.tareas ?? []
  const costos = modelo.matrizCostos ?? []

  const nodes: GraphNode[] = []
  agentes.forEach((a, i) =>
    nodes.push({
      id: `A:${a}`,
      label: a,
      x: IZQ_X,
      y: filaY(i, agentes.length),
      variant: a === 'Ficticio' ? 'default' : 'origen',
    }),
  )
  tareas.forEach((t, j) =>
    nodes.push({
      id: `T:${t}`,
      label: t,
      x: DER_X,
      y: filaY(j, tareas.length),
      variant: t === 'Ficticio' ? 'default' : 'destino',
    }),
  )

  const edges: GraphEdge[] = []
  agentes.forEach((a, i) =>
    tareas.forEach((t, j) => {
      edges.push({
        from: `A:${a}`,
        to: `T:${t}`,
        label: formatNum(costos[i]?.[j] ?? 0),
        active: false,
      })
    }),
  )
  return { nodes, edges }
}

/**
 * Grafo de un PASO (o de la solución = último paso): lee `steps[i].datos`.
 * Los estados ya vienen calculados por el backend — aquí solo se mapean a estilo:
 * 'activa'/'solucion' → destacada (+flujo como magnitud); 'descartada'/'normal' → tenue.
 */
export function grafoDePaso(datos: StepDatosRed): Grafo {
  const nodos = datos.nodos ?? []
  const aristas = datos.aristas ?? []
  // En Asignación los pasos incluyen S/T; si no viene fuente explícita, usa "S".
  const fuente = datos.fuente ?? (datos.metodo === 'ASIGNACION' && nodos.includes('S') ? 'S' : null)
  const sumidero = datos.sumidero ?? (datos.metodo === 'ASIGNACION' && nodos.includes('T') ? 'T' : null)
  const pos = layout(datos.metodo, nodos, aristas, fuente)

  const activos = new Set<string>([
    ...(datos.camino ?? []),
    ...(datos.rutaOptima ?? []),
    ...(datos.nodoActual ? [datos.nodoActual] : []),
  ])

  const nodes: GraphNode[] = nodos.map(n => {
    let variant: NodeVariant = 'default'
    if (n === fuente) variant = 'source'
    else if (n === sumidero) variant = 'sink'
    else if (activos.has(n)) variant = 'active'
    else if (datos.metodo === 'ASIGNACION') {
      if (datos.agentes?.includes(n)) variant = 'origen'
      else if (datos.tareas?.includes(n)) variant = 'destino'
    }
    return {
      id: n,
      label: n,
      sublabel: datos.distancias?.[n] != null ? `d=${formatNum(datos.distancias[n])}` : undefined,
      x: pos.get(n)?.x ?? 0.5,
      y: pos.get(n)?.y ?? 0.5,
      variant,
    }
  })

  const edges: GraphEdge[] = aristas.map(a => {
    const destacada = a.estado === 'activa' || a.estado === 'solucion'
    return {
      from: a.origen,
      to: a.destino,
      label: labelPaso(a, datos.metodo),
      value: a.flujo ?? undefined,
      active: destacada,
    }
  })

  return { nodes, edges }
}

/** Variante del nodo en la vista de modelo. */
function variantBase(
  nodo: string,
  fuente: string | null | undefined,
  sumidero: string | null | undefined,
  metodo: MetodoRed,
): NodeVariant {
  if (metodo === 'KRUSKAL') return 'default'
  if (nodo === fuente) return 'source'
  if (nodo === sumidero) return 'sink'
  return 'default'
}
