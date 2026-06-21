import { useEffect } from 'react'
import { useTableauNavigation } from '@/hooks'
import { TableauTable } from './TableauTable'
import { ResultBanner } from './ResultBanner'
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
            <span className="text-sm text-slate-600 min-w-[80px] text-center">
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
        <p className="text-sm font-semibold text-slate-700 mt-1">{titulo}</p>
        <p className="text-xs text-slate-500">{descripcion}</p>
      </CardHeader>

      {datos.varEntra && (
        <>
          <div className="px-4 pb-2 flex gap-4 text-xs text-slate-600 flex-wrap">
            <span>
              <span className="text-blue-600 font-semibold">Entra:</span> {datos.varEntra}
            </span>
            {datos.varSale && (
              <span>
                <span className="text-yellow-600 font-semibold">Sale:</span> {datos.varSale}
              </span>
            )}
          </div>
          <Separator />
        </>
      )}

      <CardContent className="pt-4">
        <TableauTable step={nav.pasoActual} highlights={nav.highlights} />
        {nav.numeroPaso === nav.totalPasos && (
          <div className="mt-4">
            <ResultBanner resultado={resultado} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
