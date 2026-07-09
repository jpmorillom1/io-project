import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
  Customized,
} from 'recharts'
import { motion } from 'motion/react'
import { popIn, stagger, T_SLOW } from '@/lib/motion'
import type { StepDatosGrafico, PuntoVertice } from '@/types/io'

interface Props {
  datos: StepDatosGrafico
}

// Paleta de colores para las restricciones
const LINE_COLORS = [
  '#14c4b6', // teal
  '#a78bfa', // purple
  '#fb923c', // orange
  '#34d399', // green
  '#f87171', // red
  '#60a5fa', // blue
]

const MONO = { fontFamily: "'JetBrains Mono', monospace" }

// SVG overlay: polígono de región factible + vértices + etiquetas
function OverlayLayer({ xAxisMap, yAxisMap, region, vertices, var1, var2 }: any) {
  const xAxisKey = Object.keys(xAxisMap ?? {})[0]
  const yAxisKey = Object.keys(yAxisMap ?? {})[0]
  if (!xAxisKey || !yAxisKey) return null

  const xScale = xAxisMap[xAxisKey].scale
  const yScale = yAxisMap[yAxisKey].scale

  const toSvg = (x: number, y: number) => ({
    cx: xScale(x) as number,
    cy: yScale(y) as number,
  })

  // Polígono de la región factible
  const polygonPoints =
    region.length >= 3
      ? region.map(([x, y]: [number, number]) => {
          const { cx, cy } = toSvg(x, y)
          return `${cx},${cy}`
        }).join(' ')
      : null

  return (
    <g>
      {/* Región factible: el contorno se traza y el relleno entra después. */}
      {polygonPoints && (
        <motion.polygon
          points={polygonPoints}
          fill="rgba(20,196,182,0.1)"
          stroke="rgba(20,196,182,0.35)"
          strokeWidth={1.5}
          strokeDasharray="5 3"
          initial={{ pathLength: 0, fillOpacity: 0 }}
          animate={{ pathLength: 1, fillOpacity: 1 }}
          transition={{ pathLength: T_SLOW, fillOpacity: { duration: 0.5, delay: 0.35 } }}
        />
      )}

      {/* Vértices: entran uno a uno, ya dibujada la región. */}
      <motion.g variants={stagger(0.07, 0.5)} initial="hidden" animate="visible">
        {(vertices as PuntoVertice[]).map((v, i) => {
          const { cx, cy } = toSvg(v.x, v.y)
          return (
            <motion.g key={i} variants={popIn} style={{ transformOrigin: `${cx}px ${cy}px` }}>
              {/* Halo del óptimo: late para que la vista aterrice en él. */}
              {v.esOptimo && (
                <motion.circle
                  cx={cx}
                  cy={cy}
                  r={14}
                  fill="rgba(20,196,182,0.12)"
                  stroke="rgba(20,196,182,0.3)"
                  strokeWidth={1}
                  animate={{ r: [14, 17, 14], opacity: [0.9, 0.35, 0.9] }}
                  transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
                />
              )}
              {/* Punto */}
              <circle
                cx={cx}
                cy={cy}
                r={v.esOptimo ? 6 : 4}
                fill={v.esOptimo ? '#14c4b6' : 'rgba(255,255,255,0.55)'}
                stroke={v.esOptimo ? '#0a7a72' : 'rgba(255,255,255,0.2)'}
                strokeWidth={1.5}
              />
              {/* Etiqueta de coordenadas */}
              <text
                x={cx + 9}
                y={cy - (v.esOptimo ? 10 : 6)}
                style={{
                  ...MONO,
                  fontSize: 10,
                  fill: v.esOptimo ? '#14c4b6' : 'var(--ij-text-secondary)',
                }}
              >
                {`(${v.x}, ${v.y})`}
              </text>
              {/* Valor Z */}
              {v.valorZ !== undefined && (
                <text
                  x={cx + 9}
                  y={cy + (v.esOptimo ? 4 : 6)}
                  style={{
                    ...MONO,
                    fontSize: 9,
                    fill: v.esOptimo ? 'rgba(20,196,182,0.8)' : 'rgba(255,255,255,0.3)',
                  }}
                >
                  {`Z=${v.valorZ}`}
                </text>
              )}
            </motion.g>
          )
        })}
      </motion.g>

      {/* Etiquetas de ejes */}
      <text
        x={xScale(0) - 8}
        y={yScale(0) + 4}
        style={{ ...MONO, fontSize: 10, fill: 'var(--ij-text-secondary)' }}
        textAnchor="end"
      >
        0
      </text>
    </g>
  )
}

// Tooltip personalizado
function CustomTooltip({ active, payload, var1, var2 }: any) {
  if (!active || !payload?.length) return null
  const d = payload[0]?.payload ?? {}
  return (
    <div
      style={{
        background: 'var(--ij-bg-editor)',
        border: '1px solid var(--ij-border)',
        padding: '6px 10px',
        borderRadius: 4,
        fontSize: 11,
        ...MONO,
        color: 'var(--ij-text-primary)',
      }}
    >
      {var1}={d.x ?? '?'}, {var2}={d.y ?? '?'}
    </div>
  )
}

export function GraficoChart({ datos }: Props) {
  const { var1, var2, xMax, yMax, lineas, vertices, region } = datos

  const tickCount = (max: number) => Math.min(Math.floor(max) + 1, 12)

  return (
    <ResponsiveContainer width="100%" height={420}>
      <ComposedChart margin={{ top: 20, right: 30, bottom: 50, left: 50 }}>
        <CartesianGrid
          strokeDasharray="2 4"
          stroke="rgba(255,255,255,0.04)"
        />

        <XAxis
          type="number"
          dataKey="x"
          domain={[0, xMax]}
          tickCount={tickCount(xMax)}
          label={{
            value: var1,
            position: 'insideBottom',
            offset: -30,
            style: { ...MONO, fill: 'var(--ij-purple)', fontSize: 12 },
          }}
          tick={{ style: { ...MONO, fontSize: 10 }, fill: 'var(--ij-text-secondary)' }}
          stroke="var(--ij-border)"
          allowDataOverflow
        />

        <YAxis
          type="number"
          dataKey="y"
          domain={[0, yMax]}
          tickCount={tickCount(yMax)}
          label={{
            value: var2,
            angle: -90,
            position: 'insideLeft',
            offset: 22,
            style: { ...MONO, fill: 'var(--ij-purple)', fontSize: 12 },
          }}
          tick={{ style: { ...MONO, fontSize: 10 }, fill: 'var(--ij-text-secondary)' }}
          stroke="var(--ij-border)"
          allowDataOverflow
        />

        {/* Ejes x=0 e y=0 */}
        <ReferenceLine x={0} stroke="rgba(255,255,255,0.15)" strokeWidth={1} />
        <ReferenceLine y={0} stroke="rgba(255,255,255,0.15)" strokeWidth={1} />

        {/* Líneas de restricciones */}
        {lineas.map((linea, i) => (
          <Line
            key={`r${i}`}
            data={linea.puntos}
            dataKey="y"
            dot={false}
            activeDot={false}
            strokeWidth={1.5}
            stroke={LINE_COLORS[i % LINE_COLORS.length]}
            name={linea.etiqueta}
            legendType="line"
            // Las restricciones se trazan en cascada, en el orden en que se formularon.
            isAnimationActive
            animationDuration={600}
            animationBegin={i * 90}
            animationEasing="ease-out"
          />
        ))}

        {/* Overlay: región + vértices */}
        <Customized
          component={(props: any) => (
            <OverlayLayer
              {...props}
              region={region}
              vertices={vertices}
              var1={var1}
              var2={var2}
            />
          )}
        />

        <Tooltip
          content={(p) => <CustomTooltip {...p} var1={var1} var2={var2} />}
        />

        {lineas.length > 0 && (
          <Legend
            wrapperStyle={{
              fontSize: 11,
              ...MONO,
              color: 'var(--ij-text-secondary)',
              paddingTop: 8,
            }}
          />
        )}
      </ComposedChart>
    </ResponsiveContainer>
  )
}
