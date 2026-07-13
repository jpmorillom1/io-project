import * as React from 'react'
import { cn } from '@/lib/utils'

export type TextareaProps = React.TextareaHTMLAttributes<HTMLTextAreaElement>

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, style, onFocus, onBlur, ...props }, ref) => {
    const [focused, setFocused] = React.useState(false)
    return (
      <textarea
        className={cn(
          'flex min-h-[80px] w-full rounded-[4px] px-3 py-2 text-[13px] leading-[22px] disabled:cursor-not-allowed disabled:opacity-50 resize-none placeholder:text-[var(--ij-text-muted)]',
          className
        )}
        style={{
          background: 'var(--ij-bg-secondary)',
          color: 'var(--ij-text-default)',
          border: focused ? '1px solid var(--ij-blue)' : '1px solid var(--ij-border)',
          outline: 'none',
          transition: 'border-color 120ms',
          ...style,
        }}
        onFocus={e => { setFocused(true); onFocus?.(e) }}
        onBlur={e => { setFocused(false); onBlur?.(e) }}
        ref={ref}
        {...props}
      />
    )
  }
)
Textarea.displayName = 'Textarea'

export { Textarea }
