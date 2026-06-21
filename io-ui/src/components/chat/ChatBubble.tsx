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
          'max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-relaxed',
          isUser
            ? 'bg-blue-600 text-white rounded-tr-sm'
            : 'bg-slate-100 text-slate-800 rounded-tl-sm'
        )}
      >
        {mensaje.texto}
      </div>
    </div>
  )
}
