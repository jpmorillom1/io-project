import { cn } from '@/lib/utils'
import type { Mensaje } from '@/types/io'

interface Props {
  mensaje: Mensaje
}

export function ChatBubble({ mensaje }: Props) {
  const isUser = mensaje.rol === 'user'
  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-[4px] px-3 py-2 leading-relaxed',
          isUser ? 'rounded-tr-none' : 'rounded-tl-none'
        )}
        style={{
          fontSize: '13px',
          background: isUser ? 'var(--ij-bg-selection)' : 'var(--ij-bg-hover)',
          color: 'var(--ij-text-default)',
        }}
      >
        {mensaje.texto}
      </div>
    </div>
  )
}
