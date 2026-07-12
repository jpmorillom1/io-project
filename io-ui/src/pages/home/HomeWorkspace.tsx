import { motion } from 'motion/react'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { ShaderBackdrop } from '@/components/home/ShaderBackdrop'
import { ModuloRotativo } from '@/components/home/ModuloRotativo'
import { AnimatedGroup } from '@/components/motion-primitives/animated-group'
import { cardGroup } from '@/lib/motion'
import { Sparkles, MessageSquareText } from 'lucide-react'

/**
 * Pantalla de bienvenida: un hero tipográfico alineado a la izquierda sobre una
 * única superficie (la del editor), sin cards anidadas.
 *
 * El glow de ondas es el ÚNICO elemento ambiental de toda la app — por eso el
 * shell va plano (ver `AppShell`) y el hero no lleva halos ni sombras de texto.
 * Un acento se lee como intención; tres, como ruido.
 */
export function HomeWorkspace() {
  return (
    <div className="flex h-full overflow-hidden gap-3">
      {/* Panel izquierdo — Asistente Pivot */}
      <div
        className="w-[420px] shrink-0 flex flex-col overflow-hidden rounded-[10px]"
        style={{
          background: 'var(--ij-bg-editor)',
          boxShadow: '0 0 0 1px var(--ij-bg-editor)',
        }}
      >
        <ChatPanel />
      </div>

      {/* Panel derecho — hero de bienvenida sobre la superficie del editor. */}
      <div
        className="flex-1 relative overflow-hidden rounded-[10px]"
        style={{ background: 'var(--ij-bg-editor)' }}
      >
        {/* Glow de esquina: el canvas va hundido en la esquina inferior derecha,
            con su centro casi sobre el vértice, así que solo asoma media onda.
            El shader pinta con alfa y la máscara radial difumina el borde; el
            blur es corto a propósito (los blur grandes sobre un canvas que se
            repinta cada fotograma son caros, sobre todo en Safari). */}
        <motion.div
          aria-hidden
          className="absolute pointer-events-none"
          initial={{ opacity: 0 }}
          animate={{ opacity: 0.32 }}
          transition={{ duration: 1.6, ease: 'easeOut', delay: 0.2 }}
          style={{
            bottom: '-85%',
            right: '-75%',
            width: '170%',
            height: '200%',
            filter: 'blur(14px)',
            WebkitMaskImage:
              'radial-gradient(closest-side at 50% 50%, #000 30%, transparent 72%)',
            maskImage:
              'radial-gradient(closest-side at 50% 50%, #000 30%, transparent 72%)',
          }}
        >
          <ShaderBackdrop rate={0.5} />
        </motion.div>

        {/* Contenido: centrado vertical real por flex, sin sesgos en vh. */}
        <div className="relative h-full overflow-y-auto">
          <div className="min-h-full flex items-center px-10 lg:px-16 py-12">
            <div className="w-full max-w-xl">
              <AnimatedGroup variants={cardGroup}>
                <p
                  className="inline-flex items-center gap-2 text-xs font-medium"
                  style={{ color: 'var(--ij-teal)' }}
                >
                  <Sparkles className="h-3.5 w-3.5" />
                  Investigación Operativa asistida por IA
                </p>

                <h1
                  className="mt-4 text-4xl sm:text-[42px] font-semibold tracking-tight leading-[1.1]"
                  style={{ color: 'var(--ij-text-primary)' }}
                >
                  Pivot IO Studio
                </h1>

                <p
                  className="mt-4 text-sm leading-relaxed max-w-lg"
                  style={{ color: 'var(--ij-text-muted)' }}
                >
                  Cuéntale tu problema al Asistente Pivot en el chat de la izquierda:
                  identificará el modelo, lo validará contigo y lo resolverá mostrando
                  el procedimiento paso a paso.
                </p>

                <div
                  className="my-9 h-px w-full"
                  style={{ background: 'var(--ij-border)' }}
                />

                <ModuloRotativo />

                <p
                  className="mt-9 flex items-start gap-2.5 text-xs leading-relaxed max-w-lg"
                  style={{ color: 'var(--ij-text-secondary)' }}
                >
                  <MessageSquareText className="h-4 w-4 shrink-0 mt-px" />
                  <span>
                    La tutoría es socrática: Pivot no entrega la respuesta de primeras.
                    Pídele que verifique tus pasos o que te explique holguras, precios
                    sombra y condiciones de optimalidad.
                  </span>
                </p>
              </AnimatedGroup>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
