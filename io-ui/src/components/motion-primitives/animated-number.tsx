import { cn } from '@/lib/utils'
import { motion, useSpring, useTransform, type SpringOptions } from 'motion/react'
import React, { useEffect } from 'react'

export type AnimatedNumberProps = {
  value: number
  className?: string
  springOptions?: SpringOptions
  as?: React.ElementType
  /** Cómo pintar cada valor intermedio de la interpolación. Por defecto, entero. */
  format?: (value: number) => string
}

export function AnimatedNumber({
  value,
  className,
  springOptions,
  as = 'span',
  format = n => Math.round(n).toLocaleString(),
}: AnimatedNumberProps) {
  const MotionComponent = React.useMemo(() => motion.create(as), [as])

  const spring = useSpring(value, springOptions)
  const display = useTransform(spring, current => format(current))

  useEffect(() => {
    spring.set(value)
  }, [spring, value])

  return <MotionComponent className={cn('tabular-nums', className)}>{display}</MotionComponent>
}
