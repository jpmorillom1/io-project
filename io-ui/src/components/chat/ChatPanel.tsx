import { useRef, useEffect, useState } from 'react'
import { useChat } from '@/hooks'
import { ChatBubble } from './ChatBubble'
import { Button } from '@/components/ui/button'
import { Loader2, Send, Bot } from 'lucide-react'

const PROMPTS_EJEMPLO = [
  'Quiero maximizar la ganancia produciendo dos productos con restricciones de recursos...',
  'Tengo un problema de dieta: minimizar costos cumpliendo requerimientos nutricionales...',
]

export function ChatPanel() {
  const { mensajes, enviar, isSending, error } = useChat()
  const [input, setInput] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensajes, isSending])

  async function handleEnviar() {
    if (!input.trim() || isSending) return
    const texto = input
    setInput('')
    await enviar(texto)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleEnviar()
    }
  }

  return (
    <div className="flex flex-col h-full bg-white border-r border-slate-200">
      {/* Header */}
      <div className="px-4 py-3 border-b border-slate-200 bg-white">
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-full bg-blue-100 flex items-center justify-center shrink-0">
            <Bot className="h-4.5 w-4.5 text-blue-600" />
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-800 leading-tight">Tutor Ío</p>
            <div className="flex items-center gap-1.5 mt-0.5">
              <span className="h-1.5 w-1.5 rounded-full bg-green-400" />
              <span className="text-xs text-slate-400">Asistente socrático de IO</span>
            </div>
          </div>
        </div>
      </div>

      {/* Mensajes */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
        {mensajes.length === 0 ? (
          /* Estado de bienvenida */
          <div className="flex flex-col gap-4 pt-4">
            <div className="text-center">
              <p className="text-sm text-slate-600 font-medium">
                Cuéntame tu problema y lo formularemos juntos
              </p>
              <p className="text-xs text-slate-400 mt-1">
                Describirlo en lenguaje natural es suficiente.
                Yo identificaré las variables, la función objetivo y las restricciones.
              </p>
            </div>

            <div className="space-y-2">
              <p className="text-xs text-slate-400 text-center">Por ejemplo:</p>
              {PROMPTS_EJEMPLO.map((p, i) => (
                <button
                  key={i}
                  onClick={() => setInput(p)}
                  className="w-full text-left text-xs px-3 py-2.5 rounded-lg border border-slate-200 hover:border-blue-300 hover:bg-blue-50 text-slate-500 hover:text-slate-700 transition-colors leading-relaxed"
                >
                  "{p}"
                </button>
              ))}
            </div>

            <p className="text-xs text-slate-400 text-center">
              El formulario se llenará automáticamente conforme avancemos.
            </p>
          </div>
        ) : (
          /* Historial de mensajes */
          <>
            {mensajes.map((m, i) => (
              <ChatBubble key={i} mensaje={m} />
            ))}
          </>
        )}

        {/* Indicador de escritura */}
        {isSending && (
          <div className="flex justify-start">
            <div className="bg-slate-100 rounded-2xl rounded-tl-sm px-3 py-2 flex items-center gap-1.5">
              <Loader2 className="h-3.5 w-3.5 animate-spin text-slate-400" />
              <span className="text-xs text-slate-400">Ío está escribiendo…</span>
            </div>
          </div>
        )}

        {error && (
          <p className="text-xs text-red-500 text-center bg-red-50 rounded-md px-3 py-2">
            {error}
          </p>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="p-3 border-t border-slate-200 bg-white">
        <div className="flex gap-2 items-end">
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe tu problema de IO…"
            rows={2}
            disabled={isSending}
            className="flex-1 resize-none text-sm border border-slate-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent disabled:opacity-60 leading-relaxed"
          />
          <Button
            size="icon"
            onClick={handleEnviar}
            disabled={isSending || !input.trim()}
            className="shrink-0 rounded-xl h-10 w-10"
          >
            <Send className="h-4 w-4" />
          </Button>
        </div>
        <p className="text-[10px] text-slate-300 mt-1.5 text-center">
          Enter para enviar · Shift+Enter para nueva línea
        </p>
      </div>
    </div>
  )
}
