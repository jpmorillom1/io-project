import { useRef, useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { T_SNAPPY } from '@/lib/motion'
import { useChat } from '@/hooks'
import { ChatBubble } from './ChatBubble'
import { ApprovalCard } from './ApprovalCard'
import { HistorialPanel } from './HistorialPanel'
import { Button } from '@/components/ui/button'
import { History, Loader2, Send, SquarePen } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import { PivotAvatar } from './PivotAvatar'

const PROMPTS_EJEMPLO = [
  'Quiero maximizar la ganancia produciendo dos productos con restricciones de recursos...',
  'Tengo un problema de transporte: 3 plantas con cierta oferta abastecen 4 ciudades con demanda, minimizando el costo de envío...',
]

export function ChatPanel() {
  const {
    mensajes, enviar, decidir, solicitud, isSending, actividad, error,
    refrescarSesiones, nuevaConversacion,
  } = useChat()
  const [input, setInput] = useState('')
  const [inputFocused, setInputFocused] = useState(false)
  const [historialAbierto, setHistorialAbierto] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensajes, isSending, solicitud])

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

  // El titulador corre en background: al abrir la lista pedimos la versión fresca para
  // que el título definitivo reemplace al recorte provisional del enunciado.
  function abrirHistorial() {
    refrescarSesiones()
    setHistorialAbierto(true)
  }

  function handleNueva() {
    nuevaConversacion()
    setInput('')
    setHistorialAbierto(false)
  }

  return (
    <div className="relative flex flex-col h-full" style={{ background: 'var(--ij-bg-editor)' }}>
      {/* Header */}
      <div className="px-4 py-3" style={{ background: 'var(--ij-bg-editor)', borderBottom: '1px solid var(--ij-bg-secondary)' }}>
        <div className="flex items-center gap-2.5">
          {/* El avatar es el indicador de estado del asistente: orbita mientras
              Pivot piensa y se detiene al terminar. De ahí que reciba `isSending`. */}
          <PivotAvatar className="shrink-0" activo={isSending} />
          <div className="flex-1 min-w-0">
            <p
              className="text-sm font-semibold leading-tight"
              style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--ij-teal)' }}
            >
              Pivot
            </p>
            {/* El estado "pensando" NO se anuncia aquí: ya lo cuentan el avatar
                (que orbita) y la píldora del hilo. Repetirlo en tres sitios es ruido. */}
            <div className="flex items-center gap-1.5 mt-0.5">
              <span
                className="h-1.5 w-1.5 rounded-full"
                style={{ background: 'var(--ij-green)' }}
              />
              <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
                Asistente Pivot
              </span>
            </div>
          </div>

          <IconoHeader
            label="Ver conversaciones"
            onClick={abrirHistorial}
            disabled={isSending}
          >
            <History className="h-4 w-4" />
          </IconoHeader>
          <IconoHeader
            label="Nueva conversación"
            onClick={handleNueva}
            disabled={isSending || mensajes.length === 0}
          >
            <SquarePen className="h-4 w-4" />
          </IconoHeader>
        </div>
      </div>

      <AnimatePresence>
        {historialAbierto && <HistorialPanel onCerrar={() => setHistorialAbierto(false)} />}
      </AnimatePresence>

      {/* Mensajes */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-3 min-h-0">
        {mensajes.length === 0 ? (
          <div className="flex flex-col gap-4 pt-4">
            <div className="text-center">
              <p className="text-sm font-medium" style={{ color: 'var(--ij-text-default)' }}>
                Cuéntame tu problema y lo formularemos juntos
              </p>
              <p className="text-xs mt-1" style={{ color: 'var(--ij-text-secondary)' }}>
                Describirlo en lenguaje natural es suficiente.
                Yo identificaré las variables, la función objetivo y las restricciones.
              </p>
            </div>

            <div className="space-y-2">
              <p className="text-xs text-center" style={{ color: 'var(--ij-text-secondary)' }}>
                Por ejemplo:
              </p>
              {PROMPTS_EJEMPLO.map((p, i) => (
                <ExamplePromptButton key={i} text={p} onClick={() => setInput(p)} />
              ))}
            </div>

            <p className="text-xs text-center" style={{ color: 'var(--ij-text-secondary)' }}>
              El formulario se llenará automáticamente conforme avancemos.
            </p>
          </div>
        ) : (
          <>
            {mensajes.map((m, i) => (
              <ChatBubble key={i} mensaje={m} />
            ))}
          </>
        )}

        {solicitud && !isSending && (
          <ApprovalCard solicitud={solicitud} onDecidir={decidir} />
        )}

        {isSending && (
          <div className="flex justify-start">
            <ShaderGlow target="pill">
              <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" style={{ color: '#fff' }} />
              {/* La fase la cuenta el backend mientras trabaja (ver ActividadRegistry).
                  Hasta que llega el primer sondeo, el texto genérico de siempre.
                  Solo fundido, sin desplazamiento: la píldora cambia de ancho con el
                  texto y un movimiento lateral encima se leería como un tirón. */}
              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={actividad?.fase ?? 'escribiendo'}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={T_SNAPPY}
                  className="whitespace-nowrap"
                >
                  {actividad?.texto ?? 'Pivot está escribiendo'}…
                </motion.span>
              </AnimatePresence>
            </ShaderGlow>
          </div>
        )}

        {error && (
          <p
            className="text-xs text-center rounded-[4px] px-3 py-2"
            style={{ color: 'var(--ij-red)', background: 'rgba(255,82,99,0.08)' }}
          >
            {error}
          </p>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="p-3" style={{ background: 'var(--ij-bg-editor)' }}>
        <div className="flex gap-2 items-end">
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe tu problema de IO…"
            rows={2}
            disabled={isSending}
            className="flex-1 resize-none rounded-[4px] px-3 py-2.5 disabled:opacity-60 placeholder:text-[var(--ij-text-muted)]"
            style={{
              fontSize: '13px',
              lineHeight: '22px',
              background: 'var(--ij-bg-secondary)',
              color: 'var(--ij-text-default)',
              border: inputFocused
                ? '1px solid var(--ij-blue)'
                : '1px solid var(--ij-border)',
              outline: 'none',
              transition: 'border-color 120ms',
            }}
            onFocus={() => setInputFocused(true)}
            onBlur={() => setInputFocused(false)}
          />
          <Button
            size="icon"
            onClick={handleEnviar}
            disabled={isSending || !input.trim()}
            className="shrink-0 rounded-[4px] h-10 w-10"
          >
            <Send className="h-4 w-4" />
          </Button>
        </div>
        <p className="mt-1.5 text-center" style={{ fontSize: '10px', color: 'var(--ij-comment)' }}>
          Enter para enviar · Shift+Enter para nueva línea
        </p>
      </div>
    </div>
  )
}

function IconoHeader({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick: () => void
  disabled: boolean
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className="shrink-0 rounded-[4px] p-1.5 transition-colors duration-[120ms] disabled:opacity-40 enabled:hover:bg-[var(--ij-bg-hover)]"
      style={{ color: 'var(--ij-text-secondary)', cursor: disabled ? 'default' : 'pointer' }}
    >
      {children}
    </button>
  )
}

function ExamplePromptButton({ text, onClick }: { text: string; onClick: () => void }) {
  const [hovered, setHovered] = useState(false)
  return (
    <button
      onClick={onClick}
      className="w-full text-left text-xs px-3 py-2.5 rounded-[4px] leading-relaxed transition-colors duration-[120ms]"
      style={{
        border: '1px solid var(--ij-border)',
        color: hovered ? 'var(--ij-text-primary)' : 'var(--ij-text-secondary)',
        background: hovered ? 'var(--ij-bg-hover)' : 'transparent',
        cursor: 'pointer',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      "{text}"
    </button>
  )
}
