import { motion } from 'motion/react'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { ShaderBackdrop } from '@/components/home/ShaderBackdrop'
import { ModuloRotativo } from '@/components/home/ModuloRotativo'
import { AnimatedGroup } from '@/components/motion-primitives/animated-group'
import { cardGroup } from '@/lib/motion'
import { Sparkles, MessageSquareText } from 'lucide-react'

/**
 * Pantalla de bienvenida. Sin cards ni rejillas: un hero tipográfico alineado a
 * la izquierda, un glow de ondas que respira desde la esquina superior derecha
 * (difuminado con blur + máscara radial) y los módulos presentados de uno en
 * uno con el rodillo de texto.
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

      {/* Panel derecho — hero de bienvenida. Sin fondo propio: se integra con el
          fondo de la app en lugar de leerse como una card oscura. */}
      <div className="flex-1 relative overflow-hidden rounded-[10px]">
        {/* Glow de esquina: el canvas va hundido en la esquina inferior derecha,
            con su centro casi sobre el vértice, de modo que solo asoma media onda.
            Blur + máscara radial difuminan los bordes; el shader pinta con alfa,
            así que no impone ningún rectángulo. Entra en fundido lento. */}
        <motion.div
          aria-hidden
          className="absolute pointer-events-none"
          initial={{ opacity: 0 }}
          animate={{ opacity: 0.6 }}
          transition={{ duration: 1.8, ease: 'easeOut', delay: 0.2 }}
          style={{
            bottom: '-85%',
            right: '-75%',
            width: '170%',
            height: '200%',
            filter: 'blur(24px)',
            WebkitMaskImage:
              'radial-gradient(closest-side at 50% 50%, #000 32%, transparent 76%)',
            maskImage:
              'radial-gradient(closest-side at 50% 50%, #000 32%, transparent 76%)',
          }}
        >
          <ShaderBackdrop rate={0.6} />
        </motion.div>

        {/* Contenido */}
        <div className="relative h-full overflow-y-auto">
          {/* El padding inferior sesga el centrado hacia arriba: el hero queda
              por encima del centro geométrico sin dejar de ser responsivo. */}
          <div
            className="min-h-full flex items-center px-8 lg:px-14 pt-8"
            style={{ paddingBottom: '17vh' }}
          >
            {/* Solo el texto va en card, con el mismo acabado plano que las cards
                del resto de la app: fondo del editor, sin sombra ni cristal. */}
            <div
              className="w-full max-w-2xl flex flex-col gap-12 rounded-[10px] p-9 lg:p-12"
              style={{ background: 'var(--ij-bg-editor)' }}
            >
            <AnimatedGroup variants={cardGroup} className="space-y-4">
              <p
                className="inline-flex items-center gap-2 text-xs font-medium"
                style={{ color: 'var(--ij-teal)' }}
              >
                <Sparkles className="h-3.5 w-3.5" />
                Investigación Operativa asistida por IA
              </p>

              <h1
                className="text-4xl sm:text-5xl font-bold tracking-tight leading-tight"
                style={{
                  color: 'var(--ij-text-primary)',
                  // Halo teal muy abierto + sombra corta que despega del fondo.
                  textShadow:
                    '0 0 32px rgba(36,194,214,0.30), 0 2px 4px rgba(0,0,0,0.45)',
                }}
              >
                Pivot IO Studio
              </h1>

              <p
                className="text-sm leading-relaxed max-w-lg"
                style={{ color: 'var(--ij-text-secondary)' }}
              >
                Cuéntale tu problema al Asistente Pivot en el chat de la izquierda:
                identificará el modelo, lo validará contigo y lo resolverá mostrando
                el procedimiento paso a paso.
              </p>
            </AnimatedGroup>

            <ModuloRotativo />

            <p
              className="flex items-start gap-2.5 text-xs leading-relaxed max-w-lg"
              style={{ color: 'var(--ij-comment)' }}
            >
              <MessageSquareText className="h-4 w-4 shrink-0 mt-0.5" />
              <span>
                La tutoría es socrática: Pivot no entrega la respuesta de primeras.
                Pídele que verifique tus pasos o que te explique holguras, precios
                sombra y condiciones de optimalidad.
              </span>
            </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
