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

function preprocessChatText(text: string): string {
  if (!text) return ''
  // Escapar signos $ que representan moneda (ej. $240.0, $380.0, $ 100) para evitar
  // que remark-math los empareje como delimitadores de fórmulas matemáticas inline.
  return text.replace(
    /(?<![\\$])\$(?=\s*\d+(?:[.,]\d+)?(?:[,.;:!?)]|\s+[a-zA-ZáéíóúÁÉÍÓÚñÑ]|\s*$))/g,
    '\\$'
  )
}

export function ChatBubble({ mensaje }: Props) {
  const isUser = mensaje.rol === 'user'
  const textoProcesado = isUser ? mensaje.texto : preprocessChatText(mensaje.texto)

  return (
    <div className={cn('flex w-full', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] min-w-0 overflow-x-auto break-words rounded-[4px] px-3 py-2 leading-relaxed',
          isUser ? 'rounded-tr-none' : 'rounded-tl-none chat-md'
        )}
        style={{
          fontSize: '13px',
          background: isUser ? 'var(--ij-bg-selection)' : 'var(--ij-bg-hover)',
          color: 'var(--ij-text-default)',
          wordBreak: 'break-word',
          overflowWrap: 'anywhere',
        }}
      >
        {isUser ? (
          mensaje.texto
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm, remarkMath]}
            rehypePlugins={[rehypeKatex]}
          >
            {textoProcesado}
          </ReactMarkdown>
        )}
      </div>
    </div>
  )
}

