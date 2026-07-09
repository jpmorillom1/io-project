import { cn } from '@/lib/utils'
import { AnimatePresence, motion, type Transition } from 'motion/react'
import {
  Children,
  cloneElement,
  useEffect,
  useId,
  useState,
  type ReactElement,
} from 'react'

/** Props que este componente lee o inyecta en cada hijo clonado. */
interface PropsHijo {
  'data-id': string
  className?: string
  children?: React.ReactNode
  'data-checked'?: string
  onClick?: () => void
  onMouseEnter?: () => void
  onMouseLeave?: () => void
}

type HijoAnimado = ReactElement<PropsHijo>

export type AnimatedBackgroundProps = {
  children: HijoAnimado[] | HijoAnimado
  defaultValue?: string
  onValueChange?: (newActiveId: string | null) => void
  className?: string
  transition?: Transition
  enableHover?: boolean
}

export function AnimatedBackground({
  children,
  defaultValue,
  onValueChange,
  className,
  transition,
  enableHover = false,
}: AnimatedBackgroundProps) {
  const [activeId, setActiveId] = useState<string | null>(null)
  const uniqueId = useId()

  const handleSetActiveId = (id: string | null) => {
    setActiveId(id)
    onValueChange?.(id)
  }

  useEffect(() => {
    if (defaultValue !== undefined) {
      setActiveId(defaultValue)
    }
  }, [defaultValue])

  return Children.map(children, (child, index) => {
    const id = child.props['data-id']

    // OJO: esto SOBRESCRIBE el onClick/onMouseEnter que traiga el hijo.
    // Si el hijo necesita reaccionar al clic, engánchalo por `onValueChange`.
    const interactionProps = enableHover
      ? {
          onMouseEnter: () => handleSetActiveId(id),
          onMouseLeave: () => handleSetActiveId(null),
        }
      : {
          onClick: () => handleSetActiveId(id),
        }

    return cloneElement(
      child,
      {
        key: index,
        className: cn('relative inline-flex', child.props.className),
        'data-checked': activeId === id ? 'true' : 'false',
        ...interactionProps,
      },
      <>
        <AnimatePresence initial={false}>
          {activeId === id && (
            <motion.div
              layoutId={`background-${uniqueId}`}
              className={cn('absolute inset-0', className)}
              transition={transition}
              initial={{ opacity: defaultValue ? 1 : 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            />
          )}
        </AnimatePresence>
        <div className="z-10">{child.props.children}</div>
      </>
    )
  })
}
