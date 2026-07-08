import { type CSSProperties, useMemo } from 'react'
import { formatNum } from '@/lib/utils'
import type { SolveStepEntera, AccionNodo } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace" }

// Color por acción del nodo (ver contrato: RAMIFICA/INCUMBENTE/PODA_COTA/PODA_INFACTIBLE).
const ACCION_COLOR: Record<AccionNodo, string> = {
  RAMIFICA: '#76AEFF',        // azul — se abre en dos hijos
  INCUMBENTE: '#6CAB74',      // verde — solución entera factible que mejora
  PODA_COTA: '#C09468',       // ocre — podado por cota
  PODA_INFACTIBLE: '#FF5263', // rojo — podado por infactibilidad
}
const COLOR_DEFAULT = '#8b93a7'

// Geometría del lienzo.
const NODE_W = 92
const NODE_H = 46
const SLOT_W = 118   // separación horizontal entre hojas
const LEVEL_H = 100  // separación vertical entre niveles
const PAD_X = 60
const PAD_Y = 40

interface Nodo {
  id: number
  padreId: number
  step: SolveStepEntera
  children: number[]
  depth: number
  slot: number   // posición horizontal (en unidades de hoja)
}

interface Props {
  steps: SolveStepEntera[]
  /** nodoId del paso actualmente seleccionado (para resaltarlo). */
  seleccionado?: number | null
}

/**
 * Árbol de Branch & Bound. Construye la jerarquía desde `padreId`, lo dispone en
 * capas (profundidad = nivel, hojas repartidas horizontalmente) y pinta cada nodo
 * coloreado por su acción. La arista hacia el padre lleva la restricción (`rama`)
 * que define el nodo. El nodo del paso seleccionado se resalta.
 */
export function EnteraTree({ steps, seleccionado }: Props) {
  const { nodos, ancho, alto } = useMemo(() => construirLayout(steps), [steps])

  if (nodos.length === 0) return null

  const centro = (n: Nodo) => ({
    x: PAD_X + n.slot * SLOT_W + NODE_W / 2,
    y: PAD_Y + n.depth * LEVEL_H + NODE_H / 2,
  })
  const byId = new Map(nodos.map(n => [n.id, n]))

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${ancho} ${alto}`}
        width="100%"
        style={{ minWidth: Math.min(ancho, 520), display: 'block' }}
        role="img"
      >
        {/* Aristas padre → hijo (con la rama que las define) */}
        {nodos.map(n => {
          if (n.padreId < 0) return null
          const padre = byId.get(n.padreId)
          if (!padre) return null
          const a = centro(padre)
          const b = centro(n)
          const p1 = { x: a.x, y: a.y + NODE_H / 2 }
          const p2 = { x: b.x, y: b.y - NODE_H / 2 }
          const rama = n.step.datos.rama
          const mid = { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 }
          return (
            <g key={`edge-${n.id}`}>
              <path
                d={`M${p1.x},${p1.y} C${p1.x},${p1.y + LEVEL_H * 0.4} ${p2.x},${p2.y - LEVEL_H * 0.4} ${p2.x},${p2.y}`}
                fill="none"
                stroke="var(--ij-text-muted)"
                strokeWidth={1.4}
                strokeOpacity={0.5}
              />
              {rama && <EdgeLabel x={mid.x} y={mid.y} text={rama} />}
            </g>
          )
        })}

        {/* Nodos */}
        {nodos.map(n => {
          const c = centro(n)
          const d = n.step.datos
          const accion = d.accion
          const color = accion ? ACCION_COLOR[accion] : COLOR_DEFAULT
          const activo = seleccionado != null && d.nodoId === seleccionado
          const z = d.zRelajacion
          const esInfactible = d.estadoRelajacion && d.estadoRelajacion !== 'OPTIMO'
          return (
            <g key={`node-${n.id}`}>
              <title>
                {`Nodo ${d.nodoId} — ${d.rama ?? 'raíz'}${z != null ? ` · z=${formatNum(z)}` : ''}${accion ? ` · ${accion}` : ''}`}
              </title>
              {activo && (
                <rect
                  x={c.x - NODE_W / 2 - 4} y={c.y - NODE_H / 2 - 4}
                  width={NODE_W + 8} height={NODE_H + 8} rx={8}
                  fill="none" stroke={color} strokeWidth={2} strokeOpacity={0.55}
                />
              )}
              <rect
                x={c.x - NODE_W / 2} y={c.y - NODE_H / 2}
                width={NODE_W} height={NODE_H} rx={6}
                fill="var(--ij-bg-editor)"
                stroke={color}
                strokeWidth={activo ? 2 : 1.5}
              />
              <rect
                x={c.x - NODE_W / 2} y={c.y - NODE_H / 2}
                width={NODE_W} height={NODE_H} rx={6}
                fill={color} fillOpacity={activo ? 0.18 : 0.1}
              />
              <text
                x={c.x} y={c.y - 8}
                textAnchor="middle" dominantBaseline="central"
                style={{ ...MONO, fontSize: 11, fontWeight: 700, fill: 'var(--ij-text-primary)' }}
              >
                Nodo {d.nodoId}
              </text>
              <text
                x={c.x} y={c.y + 9}
                textAnchor="middle" dominantBaseline="central"
                style={{ ...MONO, fontSize: 11, fill: color }}
              >
                {esInfactible ? 'infactible' : z != null ? `z = ${formatNum(z)}` : '—'}
              </text>
            </g>
          )
        })}
      </svg>
    </div>
  )
}

function EdgeLabel({ x, y, text }: { x: number; y: number; text: string }) {
  const w = text.length * 6.6 + 12
  const h = 16
  return (
    <g>
      <rect
        x={x - w / 2} y={y - h / 2} width={w} height={h} rx={h / 2}
        fill="var(--ij-bg-editor)" stroke="var(--ij-border)" strokeWidth={1} strokeOpacity={0.7}
      />
      <text
        x={x} y={y + 0.5}
        textAnchor="middle" dominantBaseline="central"
        style={{ ...MONO, fontSize: 10, fill: 'var(--ij-text-secondary)' }}
      >
        {text}
      </text>
    </g>
  )
}

/** Leyenda de colores por acción, para reusar bajo el árbol. */
export function EnteraTreeLegend() {
  const items: Array<{ label: string; color: string }> = [
    { label: 'Ramifica', color: ACCION_COLOR.RAMIFICA },
    { label: 'Incumbente', color: ACCION_COLOR.INCUMBENTE },
    { label: 'Poda por cota', color: ACCION_COLOR.PODA_COTA },
    { label: 'Poda infactible', color: ACCION_COLOR.PODA_INFACTIBLE },
  ]
  return (
    <div className="flex gap-4 flex-wrap text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
      {items.map(it => (
        <span key={it.label} className="flex items-center gap-1.5">
          <span className="inline-block h-2.5 w-2.5 rounded-[2px]" style={{ background: it.color }} />
          {it.label}
        </span>
      ))}
    </div>
  )
}

// ─── layout ────────────────────────────────────────────────────────────────────

function construirLayout(steps: SolveStepEntera[]): { nodos: Nodo[]; ancho: number; alto: number } {
  // Solo los pasos de nodo (el paso final no tiene nodoId).
  const nodos: Nodo[] = steps
    .filter(s => s.datos.nodoId != null)
    .map(s => ({
      id: s.datos.nodoId!,
      padreId: s.datos.padreId ?? -1,
      step: s,
      children: [],
      depth: 0,
      slot: 0,
    }))

  if (nodos.length === 0) return { nodos, ancho: 0, alto: 0 }

  const byId = new Map(nodos.map(n => [n.id, n]))
  let raiz: Nodo | undefined
  for (const n of nodos) {
    if (n.padreId < 0 || !byId.has(n.padreId)) {
      raiz = raiz ?? n
    } else {
      byId.get(n.padreId)!.children.push(n.id)
    }
  }
  raiz = raiz ?? nodos[0]

  // DFS: profundidad por nivel, hojas repartidas en slots consecutivos; los
  // internos se centran sobre sus hijos.
  let siguienteSlot = 0
  const visitados = new Set<number>()
  function layout(id: number, depth: number) {
    const n = byId.get(id)
    if (!n || visitados.has(id)) return
    visitados.add(id)
    n.depth = depth
    if (n.children.length === 0) {
      n.slot = siguienteSlot++
      return
    }
    for (const hijo of n.children) layout(hijo, depth + 1)
    const primero = byId.get(n.children[0])!
    const ultimo = byId.get(n.children[n.children.length - 1])!
    n.slot = (primero.slot + ultimo.slot) / 2
  }
  layout(raiz.id, 0)

  // Cualquier nodo huérfano no visitado (defensivo): apílalo al final.
  for (const n of nodos) {
    if (!visitados.has(n.id)) {
      n.slot = siguienteSlot++
      visitados.add(n.id)
    }
  }

  const maxSlot = Math.max(...nodos.map(n => n.slot))
  const maxDepth = Math.max(...nodos.map(n => n.depth))
  const ancho = PAD_X * 2 + maxSlot * SLOT_W + NODE_W
  const alto = PAD_Y * 2 + maxDepth * LEVEL_H + NODE_H
  return { nodos, ancho, alto }
}
