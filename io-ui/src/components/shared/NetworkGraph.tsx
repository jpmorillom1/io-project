import { type CSSProperties, useId } from 'react'
import { motion } from 'motion/react'
import { popIn, stagger, T_BASE } from '@/lib/motion'

/**
 * Grafo genérico de nodos y aristas (SVG plano). Es agnóstico del dominio: recibe
 * nodos con posición normalizada (x,y ∈ [0,1]) y aristas, y los pinta con el estilo
 * del sistema. Reutilizable por Transporte (grafo bipartito oferta→demanda) y por el
 * futuro módulo de Redes (Dijkstra, Kruskal, Edmonds-Karp): cada módulo aporta su
 * propio adaptador dato→grafo y reusa este componente.
 */

export type NodeVariant = 'origen' | 'destino' | 'source' | 'sink' | 'active' | 'default'

export interface GraphNode {
  id: string
  label: string
  sublabel?: string
  /** Posición normalizada en [0,1]. (0,0) = arriba-izquierda. */
  x: number
  y: number
  variant?: NodeVariant
}

export interface GraphEdge {
  from: string
  to: string
  /** Texto sobre la arista: costo, peso o flujo. */
  label?: string
  /** Magnitud (flujo/asignación); si se define, engrosa la arista activa. */
  value?: number | null
  /** Arista destacada (parte de la solución / camino / árbol). */
  active?: boolean
}

export interface ColumnLabel {
  /** x normalizado de la columna (0..1). */
  x: number
  label: string
}

interface Props {
  nodes: GraphNode[]
  edges: GraphEdge[]
  /** Aristas con punta de flecha (grafo dirigido). Por defecto true. */
  directed?: boolean
  /** Curva las aristas (estilo flujo). Por defecto true; Redes puede usar rectas. */
  curved?: boolean
  /** Encabezados de columna opcionales (p.ej. "Orígenes" / "Destinos"). */
  columnLabels?: ColumnLabel[]
  /** Alto del lienzo en px del viewBox. El ancho es responsivo. */
  height?: number
}

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace" }

const VARIANT_COLOR: Record<NodeVariant, string> = {
  origen: '#a78bfa',   // purple
  destino: '#22d3c4',  // teal
  source: '#fb923c',   // orange
  sink: '#f87171',     // red
  active: '#34d399',   // green
  default: '#8b93a7',  // gris
}

const FLOW = '#34d399'   // verde: aristas activas (flujo/solución)

const VIEW_W = 760
const NODE_R = 21
const NODE_LABEL_MAX = 6   // nº máx. de caracteres dentro del nodo (resto → …)
const EDGE_WIDTH = 1.5   // mismo grosor para rutas del modelo y de la resolución

// Ritmo vertical: header arriba, luego los nodos (con halo + sublabel) dentro del carril.
const PAD_TOP = 58      // y del centro del primer nodo
const PAD_BOTTOM = 54   // aire bajo el último nodo (para su sublabel)
const ROW_GAP = 84      // separación mínima entre nodos de una columna
const HEADER_Y = 22     // baseline del encabezado de columna
const LANE_TOP = 12     // borde superior del carril de fondo

/** Una arista no se traza: aparece. Trazarla pondría la flecha antes que la línea. */
const EDGE_VARIANTS = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: T_BASE },
}

export function NetworkGraph({
  nodes, edges, directed = true, curved = true, columnLabels, height,
}: Props) {
  const uid = useId().replace(/:/g, '')
  const maxCol = Math.max(1, ...countByColumn(nodes))
  const VIEW_H = height ?? clamp(300, PAD_TOP + PAD_BOTTOM + (maxCol - 1) * ROW_GAP, 680)
  const padX = 96
  const laneHeight = VIEW_H - LANE_TOP - 8

  const pos = new Map<string, { x: number; y: number }>()
  for (const n of nodes) {
    pos.set(n.id, {
      x: padX + n.x * (VIEW_W - 2 * padX),
      y: PAD_TOP + n.y * (VIEW_H - PAD_TOP - PAD_BOTTOM),
    })
  }

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        width="100%"
        style={{ minWidth: 420, display: 'block' }}
        role="img"
      >
        <defs>
          {/* markerUnits=userSpaceOnUse → tamaño FIJO en px, no escala con el grosor
              (si no, las aristas de flujo gruesas producían flechas enormes). */}
          <marker
            id={`arrow-active-${uid}`}
            viewBox="0 0 10 10" refX="8.5" refY="5"
            markerUnits="userSpaceOnUse" markerWidth="9" markerHeight="9"
            orient="auto-start-reverse"
          >
            <path d="M0,1.5 L9,5 L0,8.5 Z" fill={FLOW} />
          </marker>
          <marker
            id={`arrow-faint-${uid}`}
            viewBox="0 0 10 10" refX="8.5" refY="5"
            markerUnits="userSpaceOnUse" markerWidth="9" markerHeight="9"
            orient="auto-start-reverse"
          >
            <path d="M0,1.5 L9,5 L0,8.5 Z" fill="var(--ij-text-muted)" />
          </marker>
        </defs>

        {/* Carriles de columna (muy sutiles) + encabezados */}
        {columnLabels?.map((c, i) => {
          const cx = padX + c.x * (VIEW_W - 2 * padX)
          return (
            <g key={`col-${i}`}>
              <rect
                x={cx - (NODE_R + 24)} y={LANE_TOP}
                width={(NODE_R + 24) * 2} height={laneHeight}
                rx={16}
                fill="var(--ij-text-primary)" fillOpacity={0.025}
              />
              <text
                x={cx} y={HEADER_Y} textAnchor="middle"
                style={{ ...MONO, fontSize: 11, letterSpacing: 1.5, fill: 'var(--ij-text-muted)' }}
              >
                {c.label.toUpperCase()}
              </text>
            </g>
          )
        })}

        {/* Aristas (detrás de los nodos) */}
        <motion.g variants={stagger(0.035)} initial="hidden" animate="visible">
        {edges.map((e, i) => {
          const a = pos.get(e.from)
          const b = pos.get(e.to)
          if (!a || !b) return null

          const dx = b.x - a.x
          const dy = b.y - a.y
          const len = Math.hypot(dx, dy) || 1
          const ux = dx / len
          const uy = dy / len
          // Recorta la línea al borde de cada nodo (deja un respiro de superficie).
          const gap = NODE_R + 3
          const p1 = { x: a.x + ux * gap, y: a.y + uy * gap }
          const p2 = { x: b.x - ux * gap, y: b.y - uy * gap }

          const width = EDGE_WIDTH
          const color = e.active ? FLOW : 'var(--ij-text-muted)'
          const marker = directed
            ? `url(#${e.active ? `arrow-active-${uid}` : `arrow-faint-${uid}`})`
            : undefined

          const { d, mid } = curved
            ? bezierPath(p1, p2)
            : { d: `M${p1.x},${p1.y} L${p2.x},${p2.y}`, mid: { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 } }

          return (
            <motion.g key={i} variants={EDGE_VARIANTS}>
              <path
                d={d}
                fill="none"
                stroke={color}
                strokeWidth={width}
                strokeLinecap="round"
                strokeOpacity={e.active ? 0.92 : 0.5}
                markerEnd={marker}
              />
              {e.label != null && e.label !== '' && (
                <EdgeLabel x={mid.x} y={mid.y} text={e.label} active={!!e.active} />
              )}
            </motion.g>
          )
        })}
        </motion.g>

        {/* Nodos — entran después de las aristas. */}
        <motion.g variants={stagger(0.04, 0.12)} initial="hidden" animate="visible">
        {nodes.map(n => {
          const p = pos.get(n.id)!
          const color = VARIANT_COLOR[n.variant ?? 'default']
          const ficticio = n.variant === 'default'
          const recortado = truncar(n.label, NODE_LABEL_MAX)
          return (
            <motion.g
              key={n.id}
              variants={popIn}
              style={{ transformOrigin: `${p.x}px ${p.y}px` }}
            >
              <title>{n.label}{n.sublabel ? ` — ${n.sublabel}` : ''}</title>
              {/* Halo */}
              <circle cx={p.x} cy={p.y} r={NODE_R + 5} fill={color} fillOpacity={0.08} />
              {/* Cuerpo */}
              <circle
                cx={p.x} cy={p.y} r={NODE_R}
                fill="var(--ij-bg-editor)"
                stroke={color}
                strokeWidth={2}
                strokeDasharray={ficticio ? '3 3' : undefined}
              />
              <circle cx={p.x} cy={p.y} r={NODE_R - 3.5} fill={color} fillOpacity={0.14} />
              <text
                x={p.x} y={p.y}
                textAnchor="middle" dominantBaseline="central"
                style={{ ...MONO, fontSize: recortado.length > 4 ? 10.5 : 12, fontWeight: 700, fill: 'var(--ij-text-primary)' }}
              >
                {recortado}
              </text>
              {n.sublabel && (
                <text
                  x={p.x} y={p.y + NODE_R + 15}
                  textAnchor="middle"
                  style={{ ...MONO, fontSize: 10.5, fill: 'var(--ij-text-secondary)' }}
                >
                  {n.sublabel}
                </text>
              )}
            </motion.g>
          )
        })}
        </motion.g>
      </svg>
    </div>
  )
}

function EdgeLabel({ x, y, text, active }: { x: number; y: number; text: string; active: boolean }) {
  const w = text.length * 6.8 + 12
  const h = 17
  return (
    <g>
      <rect
        x={x - w / 2} y={y - h / 2} width={w} height={h} rx={h / 2}
        fill="var(--ij-bg-editor)"
        stroke={active ? FLOW : 'var(--ij-border)'}
        strokeWidth={active ? 1.25 : 1}
        strokeOpacity={active ? 0.8 : 0.6}
      />
      <text
        x={x} y={y + 0.5}
        textAnchor="middle" dominantBaseline="central"
        style={{
          ...MONO,
          fontSize: 10.5,
          fontWeight: active ? 700 : 400,
          fill: active ? 'var(--ij-text-primary)' : 'var(--ij-text-secondary)',
        }}
      >
        {text}
      </text>
    </g>
  )
}

// ─── geometría ───────────────────────────────────────────────────────────────

type Pt = { x: number; y: number }

/** Curva cúbica con manijas alineadas al eje dominante (estilo diagrama de flujo). */
function bezierPath(p1: Pt, p2: Pt): { d: string; mid: Pt } {
  const dx = p2.x - p1.x
  const dy = p2.y - p1.y
  const horizontal = Math.abs(dx) >= Math.abs(dy)
  const k = 0.42
  const c1: Pt = horizontal ? { x: p1.x + dx * k, y: p1.y } : { x: p1.x, y: p1.y + dy * k }
  const c2: Pt = horizontal ? { x: p2.x - dx * k, y: p2.y } : { x: p2.x, y: p2.y - dy * k }
  const mid = cubicAt(0.5, p1, c1, c2, p2)
  return { d: `M${p1.x},${p1.y} C${c1.x},${c1.y} ${c2.x},${c2.y} ${p2.x},${p2.y}`, mid }
}

function cubicAt(t: number, p0: Pt, p1: Pt, p2: Pt, p3: Pt): Pt {
  const mt = 1 - t
  const a = mt * mt * mt
  const b = 3 * mt * mt * t
  const c = 3 * mt * t * t
  const d = t * t * t
  return { x: a * p0.x + b * p1.x + c * p2.x + d * p3.x, y: a * p0.y + b * p1.y + c * p2.y + d * p3.y }
}

// ─── utilidades ──────────────────────────────────────────────────────────────

function clamp(min: number, v: number, max: number) {
  return Math.max(min, Math.min(v, max))
}

/** Recorta una etiqueta larga a `max` caracteres con puntos suspensivos. */
function truncar(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, Math.max(1, max - 1)) + '…'
}

/** Cuenta nodos por “columna” (misma x aproximada) para dimensionar el alto. */
function countByColumn(nodes: GraphNode[]): number[] {
  const cols = new Map<number, number>()
  for (const n of nodes) {
    const key = Math.round(n.x * 20)
    cols.set(key, (cols.get(key) ?? 0) + 1)
  }
  return [...cols.values()]
}
