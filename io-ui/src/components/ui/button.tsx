import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  // El `active:scale` es el acuse de recibo de la pulsación: 2 % de hundimiento,
  // suficiente para sentirse, invisible como movimiento. La transición se acota a
  // color + transform; `transition-all` arrastraría layout fuera de la GPU.
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[4px] text-[13px] font-medium transition-[color,background-color,border-color,transform] duration-[120ms] ease-out active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ij-blue)] focus-visible:ring-offset-1 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default:
          'bg-[rgba(118,174,255,0.1)] text-[var(--ij-blue)] border border-[var(--ij-blue)] hover:bg-[rgba(118,174,255,0.18)]',
        secondary:
          'bg-[var(--ij-bg-selection)] text-[var(--ij-text-primary)] hover:bg-[var(--ij-bg-active)]',
        outline:
          'border border-[var(--ij-border)] bg-transparent text-[var(--ij-text-primary)] hover:bg-[var(--ij-bg-hover)]',
        ghost:
          'hover:bg-[var(--ij-bg-hover)] text-[var(--ij-text-secondary)] hover:text-[var(--ij-text-primary)]',
        destructive:
          'bg-[var(--ij-red)] text-white hover:opacity-90',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm:      'h-8 rounded-[4px] px-3 text-xs',
        lg:      'h-10 rounded-[4px] px-8',
        icon:    'h-9 w-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button'
    return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />
  }
)
Button.displayName = 'Button'

export { Button, buttonVariants }
