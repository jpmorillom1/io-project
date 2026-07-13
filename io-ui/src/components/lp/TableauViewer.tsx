import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { useTableauNavigation } from '@/hooks'
import { TableauTable } from './TableauTable'
import { ResultBanner } from './ResultBanner'
import { TransitionPanel } from '@/components/motion-primitives/transition-panel'
import { panelSlide, T_BASE, T_SNAPPY } from '@/lib/motion'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import type { SolveResult } from '@/types/io'
import { ChevronLeft, ChevronRight } from 'lucide-react'

interface Props {
  resultado: SolveResult
}

export function TableauViewer({ resultado }: Props) {
  const { steps } = resultado
  const nav = useTableauNavigation(steps)

  useEffect(() => {
    nav.resetIndex()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultado])

  if (!nav.pasoActual) return null

  const { titulo, descripcion, datos } = nav.pasoActual
  const esUltimoPaso = nav.numeroPaso === nav.totalPasos

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle>③ Tableau paso a paso</CardTitle>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon"
              onClick={nav.irAnterior}
              disabled={!nav.puedeAnterior}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span
              className="text-sm min-w-[80px] text-center"
              style={{
                fontFamily: "'JetBrains Mono', monospace",
                color: 'var(--ij-text-secondary)',
              }}
            >
              {nav.numeroPaso} / {nav.totalPasos}
            </span>
            <Button
              variant="outline"
              size="icon"
              onClick={nav.irSiguiente}
              disabled={!nav.puedeSiguiente}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Título y descripción se renuevan con el paso, sin desplazar la tabla. */}
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={nav.index}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={T_SNAPPY}
          >
            <p className="text-sm font-semibold mt-1" style={{ color: 'var(--ij-text-primary)' }}>
              {titulo}
            </p>
            <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
              {descripcion}
            </p>
          </motion.div>
        </AnimatePresence>
      </CardHeader>

      {datos.varEntra && (
        <>
          <div className="px-4 pb-2 flex gap-4 text-xs flex-wrap">
            <span>
              <span
                className="font-semibold"
                style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-cyan)' }}
              >
                Entra:
              </span>{' '}
              <motion.span
                key={`entra-${datos.varEntra}`}
                initial={{ opacity: 0, y: -3 }}
                animate={{ opacity: 1, y: 0 }}
                transition={T_SNAPPY}
                style={{
                  display: 'inline-block',
                  fontFamily: "'JetBrains Mono', monospace",
                  color: 'var(--ij-purple)',
                }}
              >
                {datos.varEntra}
              </motion.span>
            </span>
            {datos.varSale && (
              <span>
                <span
                  className="font-semibold"
                  style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-orange)' }}
                >
                  Sale:
                </span>{' '}
                <motion.span
                  key={`sale-${datos.varSale}`}
                  initial={{ opacity: 0, y: -3 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={T_SNAPPY}
                  style={{
                    display: 'inline-block',
                    fontFamily: "'JetBrains Mono', monospace",
                    color: 'var(--ij-purple)',
                  }}
                >
                  {datos.varSale}
                </motion.span>
              </span>
            )}
          </div>
          <Separator />
        </>
      )}

      <CardContent className="pt-4">
        {/* Solo se monta el paso activo; el resto son huecos que TransitionPanel ignora. */}
        <TransitionPanel
          activeIndex={nav.index}
          custom={nav.direccion}
          variants={panelSlide}
          transition={T_BASE}
        >
          {steps.map((_, i) =>
            i === nav.index ? (
              <TableauTable step={nav.pasoActual} highlights={nav.highlights} />
            ) : null
          )}
        </TransitionPanel>

        <AnimatePresence>
          {esUltimoPaso && (
            <motion.div
              className="mt-4"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              transition={{ ...T_BASE, delay: 0.15 }}
            >
              <ResultBanner resultado={resultado} />
            </motion.div>
          )}
        </AnimatePresence>
      </CardContent>
    </Card>
  )
}
