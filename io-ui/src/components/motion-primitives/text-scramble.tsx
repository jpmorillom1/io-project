import { useEffect, useMemo, useState } from 'react'
import { motion, type MotionProps } from 'motion/react'

export type TextScrambleProps = {
  children: string
  duration?: number
  speed?: number
  characterSet?: string
  as?: React.ElementType
  className?: string
  trigger?: boolean
  onScrambleComplete?: () => void
} & MotionProps

const defaultChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

export function TextScramble({
  children,
  duration = 0.8,
  speed = 0.04,
  characterSet = defaultChars,
  className,
  as: Component = 'p',
  trigger = true,
  onScrambleComplete,
  ...props
}: TextScrambleProps) {
  // Memoizado: el scramble re-renderiza cada tick y un motion.create sin memo
  // remontaría el nodo DOM en cada uno.
  const MotionComponent = useMemo(() => motion.create(Component), [Component])
  const [scrambledText, setScrambledText] = useState<string | null>(null)
  const [isAnimating, setIsAnimating] = useState(false)
  const text = children
  const displayText = scrambledText ?? children

  const scramble = () => {
    if (isAnimating) return
    setIsAnimating(true)

    const steps = duration / speed
    let step = 0

    const interval = setInterval(() => {
      let scrambled = ''
      const progress = step / steps

      for (let i = 0; i < text.length; i++) {
        if (text[i] === ' ') {
          scrambled += ' '
          continue
        }

        if (progress * text.length > i) {
          scrambled += text[i]
        } else {
          scrambled += characterSet[Math.floor(Math.random() * characterSet.length)]
        }
      }

      setScrambledText(scrambled)
      step++

      if (step > steps) {
        clearInterval(interval)
        setScrambledText(null)
        setIsAnimating(false)
        onScrambleComplete?.()
      }
    }, speed * 1000)
  }

  useEffect(() => {
    if (!trigger) return
    scramble()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trigger])

  return (
    <MotionComponent className={className} {...props}>
      {displayText}
    </MotionComponent>
  )
}
