import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
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
          isUser ? 'rounded-tr-none' : 'rounded-tl-none chat-md'
        )}
        style={{
          fontSize: '13px',
          background: isUser ? 'var(--ij-bg-selection)' : 'var(--ij-bg-hover)',
          color: 'var(--ij-text-default)',
        }}
      >
        {isUser ? (
          mensaje.texto
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm, remarkMath]}
            rehypePlugins={[rehypeKatex]}
          >
            {mensaje.texto}
          </ReactMarkdown>
        )}
      </div>
    </div>
  )
}
