import * as React from 'react'
import { cn } from '@/lib/utils'

export type InputProps = React.InputHTMLAttributes<HTMLInputElement>

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, ...props }, ref) => (
    <input
      type={type}
      className={cn(
        'flex h-8 w-full rounded-[4px] border border-[var(--ij-border)] bg-[var(--ij-bg-editor)] px-2.5 py-1 text-[13px] text-[var(--ij-text-primary)] transition-colors duration-[120ms] file:border-0 file:bg-transparent file:text-[13px] file:font-medium placeholder:text-[var(--ij-text-secondary)] focus-visible:outline-none focus-visible:border-[var(--ij-blue)] disabled:cursor-not-allowed disabled:opacity-50',
        className
      )}
      ref={ref}
      {...props}
    />
  )
)
Input.displayName = 'Input'

export { Input }
