import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { swapFade } from '@/lib/motion'
import {
  Sigma, Truck, Network, Binary, Package, Workflow,
  ArrowRight, type LucideIcon,
} from 'lucide-react'

/**
 * Presenta los módulos de uno en uno. El bloque se reemplaza con un crossfade
 * corto (`swapFade`), no con un descifrado letra a letra: el home se mira de
 * fondo mientras se escribe en el chat, y un texto en movimiento perpetuo en
 * visión periférica es ruido, no información.
 *
 * Por lo mismo el ciclo se detiene mientras el puntero está encima o el foco
 * dentro: si el usuario está leyendo, el carrusel no le cambia el texto debajo.
 */

interface Modulo {
  id: string
  title: string
  subtitle: string
  description: string
  icon: LucideIcon
  color: string
  path: string
}

const MODULOS: Modulo[] = [
  {
    id: 'lp',
    title: 'Programación Lineal',
    subtitle: 'Simplex · Gráfico · Gran M · Dos Fases',
    description:
      'Formulación algebraica, tablas Simplex paso a paso, análisis de sensibilidad y región factible en 2D.',
    icon: Sigma,
    color: 'var(--ij-teal)',
    path: '/lp',
  },
  {
    id: 'transporte',
    title: 'Transporte y Asignación',
    subtitle: 'Noroeste · Costo Mínimo · Vogel · MODI',
    description:
      'Matrices de costos, balanceo automático de oferta y demanda, y optimización iterativa con MODI.',
    icon: Truck,
    color: 'var(--ij-purple)',
    path: '/transporte',
  },
  {
    id: 'redes',
    title: 'Modelos de Redes',
    subtitle: 'Dijkstra · Kruskal · Flujo Máximo · Costo Mínimo',
    description:
      'Ruta más corta, árbol de expansión mínima y flujos óptimos sobre grafos interactivos.',
    icon: Network,
    color: 'var(--ij-orange)',
    path: '/redes',
  },
  {
    id: 'pl-entera',
    title: 'PL Entera y Binaria',
    subtitle: 'Branch & Bound · Relajación Lineal',
    description:
      'Árbol de ramificación y acotamiento para variables enteras y binarias, explicado nodo a nodo.',
    icon: Binary,
    color: 'var(--ij-green)',
    path: '/pl-entera',
  },
  {
    id: 'inventario',
    title: 'Modelos de Inventario',
    subtitle: 'EOQ · Faltantes · Descuentos · Producción',
    description:
      'Lote económico, punto de reorden y comparación de costos totales entre políticas.',
    icon: Package,
    color: 'var(--ij-amber)',
    path: '/inventario',
  },
  {
    id: 'dinamica',
    title: 'Programación Dinámica',
    subtitle: 'Etapas · Mochila · Producción · Reemplazo',
    description:
      'Descomposición por etapas, recursión hacia atrás y recuperación de la política óptima.',
    icon: Workflow,
    color: 'var(--ij-blue)',
    path: '/dinamica',
  },
]

/** Cuánto se queda en pantalla cada módulo antes de ceder el turno. */
const PAUSA_MS = 6000

export function ModuloRotativo() {
  const navigate = useNavigate()
  const [indice, setIndice] = useState(0)
  const [pausado, setPausado] = useState(false)

  const mod = MODULOS[indice]
  const Icon = mod.icon

  useEffect(() => {
    if (pausado) return
    const id = window.setInterval(
      () => setIndice(i => (i + 1) % MODULOS.length),
      PAUSA_MS
    )
    return () => window.clearInterval(id)
  }, [pausado])

  return (
    <div
      className="space-y-6"
      onMouseEnter={() => setPausado(true)}
      onMouseLeave={() => setPausado(false)}
      onFocusCapture={() => setPausado(true)}
      onBlurCapture={() => setPausado(false)}
    >
      <p
        className="text-[10px] font-medium"
        style={{
          fontFamily: "'JetBrains Mono', monospace",
          letterSpacing: '2px',
          color: 'var(--ij-text-secondary)',
        }}
      >
        MÓDULOS · {String(indice + 1).padStart(2, '0')} / {String(MODULOS.length).padStart(2, '0')}
      </p>

      {/* La altura mínima reserva el sitio del bloque más alto: al reemplazarse el
          contenido, el pie del hero no debe saltar. */}
      <div style={{ minHeight: '132px' }}>
        <AnimatePresence mode="wait" initial={false}>
          <motion.button
            key={mod.id}
            variants={swapFade}
            initial="initial"
            animate="animate"
            exit="exit"
            onClick={() => navigate(mod.path)}
            className="group block text-left w-full"
            style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
          >
            <div className="flex items-center gap-3">
              <Icon
                className="h-6 w-6 shrink-0"
                strokeWidth={1.75}
                style={{ color: mod.color }}
              />
              <h2
                className="text-xl font-semibold tracking-tight"
                style={{ color: 'var(--ij-text-primary)' }}
              >
                {mod.title}
              </h2>
            </div>

            {/* Los algoritmos van en monoespaciada — son nombres propios de método,
                se leen como identificadores. La descripción, en cambio, es prosa y
                va en la fuente del cuerpo. */}
            <p
              className="mt-3 text-[11px]"
              style={{ fontFamily: "'JetBrains Mono', monospace", color: mod.color }}
            >
              {mod.subtitle}
            </p>

            <p
              className="mt-2 text-[13px] leading-relaxed max-w-md"
              style={{ color: 'var(--ij-text-muted)' }}
            >
              {mod.description}
            </p>

            <span
              className="mt-3 inline-flex items-center gap-1.5 text-xs font-medium"
              style={{ color: mod.color }}
            >
              Abrir módulo
              <ArrowRight className="h-3.5 w-3.5 transition-transform duration-[160ms] group-hover:translate-x-0.5" />
            </span>
          </motion.button>
        </AnimatePresence>
      </div>

      {/* Puntos de progreso: el activo se alarga y toma el color del módulo.
          La transición se acota a `width`/`background-color` — `transition-all`
          arrastraría propiedades que no queremos animar. */}
      <div className="flex items-center gap-1.5">
        {MODULOS.map((m, i) => (
          <button
            key={m.id}
            onClick={() => setIndice(i)}
            title={m.title}
            aria-label={m.title}
            aria-current={i === indice}
            className="h-1 rounded-full transition-[width,background-color] duration-[200ms]"
            style={{
              width: i === indice ? '20px' : '6px',
              background: i === indice ? mod.color : 'var(--ij-bg-active)',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
            }}
          />
        ))}
      </div>
    </div>
  )
}
