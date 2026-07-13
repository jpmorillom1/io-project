import { AnimatedNumber } from '@/components/motion-primitives/animated-number'
import { SPRING_NUMBER } from '@/lib/motion'
import { formatNum } from '@/lib/utils'

/**
 * Cuántos decimales muestra `formatNum` para este valor. El contador debe pintar
 * todos sus fotogramas con ese mismo ancho; si no, el número tiembla al subir.
 */
function decimalesDe(n: number): number {
  const texto = formatNum(n)
  const punto = texto.indexOf('.')
  return punto === -1 ? 0 : texto.length - punto - 1
}

interface Props {
  value: number
  className?: string
}

/**
 * Cifra titular (Z*, costo total, Q*) que cuenta hasta su valor final.
 * Aterriza exactamente en lo que habría impreso `formatNum`.
 */
export function ValorAnimado({ value, className }: Props) {
  const decimales = decimalesDe(value)
  return (
    <AnimatedNumber
      value={value}
      className={className}
      springOptions={SPRING_NUMBER}
      format={n => n.toFixed(decimales)}
    />
  )
}
