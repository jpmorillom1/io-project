import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { TextScramble } from '@/components/motion-primitives/text-scramble'
import { T_SNAPPY } from '@/lib/motion'
import {
  Sigma, Truck, Network, Binary, Package, Workflow,
  ArrowRight, type LucideIcon,
} from 'lucide-react'

/**
 * Presenta los módulos de UNO en uno, sin cards: el título se descifra letra a
 * letra (TextScramble) y el resto del bloque entra en fundido. El scramble solo
 * corre al montarse, así que el ciclo se encadena: al completarse se espera
 * `PAUSA_MS` y se avanza el índice; el `key` fuerza el remontaje.
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
    color: '#24C2D6',
    path: '/lp',
  },
  {
    id: 'transporte',
    title: 'Transporte y Asignación',
    subtitle: 'Noroeste · Costo Mínimo · Vogel · MODI',
    description:
      'Matrices de costos, balanceo automático de oferta y demanda, y optimización iterativa con MODI.',
    icon: Truck,
    color: '#B07CFF',
    path: '/transporte',
  },
  {
    id: 'redes',
    title: 'Modelos de Redes',
    subtitle: 'Dijkstra · Kruskal · Flujo Máximo · Costo Mínimo',
    description:
      'Ruta más corta, árbol de expansión mínima y flujos óptimos sobre grafos interactivos.',
    icon: Network,
    color: '#F2801A',
    path: '/redes',
  },
  {
    id: 'pl-entera',
    title: 'PL Entera y Binaria',
    subtitle: 'Branch & Bound · Relajación Lineal',
    description:
      'Árbol de ramificación y acotamiento para variables enteras y binarias, explicado nodo a nodo.',
    icon: Binary,
    color: '#6CAB74',
    path: '/pl-entera',
  },
  {
    id: 'inventario',
    title: 'Modelos de Inventario',
    subtitle: 'EOQ · Faltantes · Descuentos · Producción',
    description:
      'Lote económico, punto de reorden y comparación de costos totales entre políticas.',
    icon: Package,
    color: '#FFC859',
    path: '/inventario',
  },
  {
    id: 'dinamica',
    title: 'Programación Dinámica',
    subtitle: 'Etapas · Mochila · Producción · Reemplazo',
    description:
      'Descomposición por etapas, recursión hacia atrás y recuperación de la política óptima.',
    icon: Workflow,
    color: '#CF84CF',
    path: '/dinamica',
  },
]

/** Cuánto se queda quieto cada módulo una vez el título terminó de rodar. */
const PAUSA_MS = 5000

export function ModuloRotativo() {
  const navigate = useNavigate()
  const [indice, setIndice] = useState(0)
  const timerRef = useRef<number | null>(null)

  const mod = MODULOS[indice]
  const Icon = mod.icon

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
  }, [])

  function alTerminarScramble() {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(() => {
      setIndice(i => (i + 1) % MODULOS.length)
    }, PAUSA_MS)
  }

  /** Salto manual desde los puntos: detiene el ciclo en curso y reancla. */
  function irA(i: number) {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    setIndice(i)
  }

  return (
    <div className="space-y-5">
      <p
        className="text-[11px] font-medium"
        style={{
          fontFamily: "'JetBrains Mono', monospace",
          letterSpacing: '2.5px',
          color: 'var(--ij-text-secondary)',
        }}
      >
        MÓDULOS · {String(indice + 1).padStart(2, '0')}/{String(MODULOS.length).padStart(2, '0')}
      </p>

      {/* Todo el bloque navega al módulo en pantalla. Sin card: solo tipografía. */}
      <button
        onClick={() => navigate(mod.path)}
        className="group block text-left w-full"
        style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
      >
        <div className="flex items-center gap-3.5">
          <AnimatePresence mode="wait" initial={false}>
            <motion.span
              key={indice}
              initial={{ opacity: 0, scale: 0.7 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.7 }}
              transition={T_SNAPPY}
              className="shrink-0"
              style={{ color: mod.color }}
            >
              <Icon className="h-7 w-7" strokeWidth={1.75} />
            </motion.span>
          </AnimatePresence>

          {/* El título se descifra letra a letra (scramble). Va en monoespaciada
              a propósito: con una fuente proporcional los caracteres aleatorios
              harían bailar el ancho del texto en cada tick. El span exterior
              aporta color y fuente porque TextScramble no acepta style. */}
          <span
            style={{ color: mod.color, fontFamily: "'JetBrains Mono', monospace" }}
          >
            <TextScramble
              key={indice}
              as="span"
              className="block text-2xl sm:text-3xl font-bold tracking-tight"
              duration={1}
              speed={0.03}
              onScrambleComplete={alTerminarScramble}
            >
              {mod.title}
            </TextScramble>
          </span>
        </div>

        {/* Subtítulo y descripción se descifran a la vez que el título: mismo
            key, mismo duration/speed, así que arrancan y terminan juntos. Van
            en mono también por estabilidad: como cada carácter aleatorio mide
            lo mismo que el real, el salto de línea no baila durante el efecto.
            La altura mínima evita que el bloque salte entre descripciones. */}
        <div style={{ minHeight: '96px' }} className="mt-5">
          <div key={indice} className="space-y-2">
            <p
              className="text-xs"
              style={{
                fontFamily: "'JetBrains Mono', monospace",
                color: mod.color,
                opacity: 0.85,
              }}
            >
              <TextScramble as="span" duration={1} speed={0.03}>
                {mod.subtitle}
              </TextScramble>
            </p>
            <p
              className="text-[13px] leading-relaxed max-w-md"
              style={{
                fontFamily: "'JetBrains Mono', monospace",
                color: 'var(--ij-text-secondary)',
              }}
            >
              <TextScramble as="span" duration={1} speed={0.03}>
                {mod.description}
              </TextScramble>
            </p>
            <span
              className="inline-flex items-center gap-1.5 text-xs font-medium pt-1"
              style={{ color: mod.color }}
            >
              Abrir módulo
              <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" />
            </span>
          </div>
        </div>
      </button>

      {/* Puntos de progreso: el activo se alarga y toma el color del módulo. */}
      <div className="flex items-center gap-2">
        {MODULOS.map((m, i) => (
          <button
            key={m.id}
            onClick={() => irA(i)}
            title={m.title}
            aria-label={m.title}
            className="h-1.5 rounded-full transition-all duration-300"
            style={{
              width: i === indice ? '24px' : '8px',
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
