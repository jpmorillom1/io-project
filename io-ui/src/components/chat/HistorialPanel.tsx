import { motion } from 'motion/react'
import { MessageSquare, X } from 'lucide-react'
import { useChat } from '@/hooks'
import { T_BASE, stagger, revealRow } from '@/lib/motion'
import type { ModuloActivo, ResumenSesion } from '@/types/io'

/** Etiqueta corta del módulo, para reconocer la conversación de un vistazo. */
const NOMBRE_MODULO: Record<ModuloActivo, string> = {
  PL: 'Prog. lineal',
  ENTERA: 'PL entera',
  TRANSPORTE: 'Transporte',
  REDES: 'Redes',
  INVENTARIO: 'Inventarios',
  DINAMICA: 'Prog. dinámica',
  GENERAL: 'General',
}

const MINUTO = 60_000
const HORA = 60 * MINUTO
const DIA = 24 * HORA

/** "hace 5 min", "hace 3 h", "hace 2 d" — suficiente para ordenar mentalmente la lista. */
function haceCuanto(iso: string): string {
  const transcurrido = Date.now() - new Date(iso).getTime()
  if (transcurrido < MINUTO) return 'ahora'
  if (transcurrido < HORA) return `hace ${Math.floor(transcurrido / MINUTO)} min`
  if (transcurrido < DIA) return `hace ${Math.floor(transcurrido / HORA)} h`
  return `hace ${Math.floor(transcurrido / DIA)} d`
}

/**
 * Lista de conversaciones anteriores. Se monta sobre el chat, no junto a él: el
 * layout ya tiene rail + workspace + chat y una cuarta columna fija lo estrecharía.
 */
export function HistorialPanel({ onCerrar }: { onCerrar: () => void }) {
  const { sesiones, sesionActivaId, abrirSesion, isSending } = useChat()

  async function seleccionar(id: string) {
    await abrirSesion(id)
    onCerrar()
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: -12 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -12 }}
      transition={T_BASE}
      className="absolute inset-0 z-20 flex flex-col"
      style={{ background: 'var(--ij-bg-editor)' }}
    >
      <div
        className="flex items-center justify-between px-4 py-3 shrink-0"
        style={{ borderBottom: '1px solid var(--ij-bg-secondary)' }}
      >
        <p className="text-sm font-semibold" style={{ color: 'var(--ij-text-default)' }}>
          Conversaciones
        </p>
        <button
          onClick={onCerrar}
          aria-label="Cerrar historial"
          className="rounded-[4px] p-1 transition-colors duration-[120ms] hover:bg-[var(--ij-bg-hover)]"
          style={{ color: 'var(--ij-text-secondary)', cursor: 'pointer' }}
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-2 min-h-0">
        {sesiones.length === 0 ? (
          <p className="text-xs text-center px-4 pt-8" style={{ color: 'var(--ij-text-secondary)' }}>
            Todavía no hay conversaciones guardadas. La primera nace cuando envíes un mensaje.
          </p>
        ) : (
          <motion.ul variants={stagger()} initial="hidden" animate="visible" className="space-y-1">
            {sesiones.map(sesion => (
              <motion.li key={sesion.sesionId} variants={revealRow}>
                <ItemSesion
                  sesion={sesion}
                  activa={sesion.sesionId === sesionActivaId}
                  deshabilitada={isSending}
                  onClick={() => seleccionar(sesion.sesionId)}
                />
              </motion.li>
            ))}
          </motion.ul>
        )}
      </div>
    </motion.div>
  )
}

function ItemSesion({
  sesion,
  activa,
  deshabilitada,
  onClick,
}: {
  sesion: ResumenSesion
  activa: boolean
  deshabilitada: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      disabled={deshabilitada}
      className="w-full text-left px-3 py-2.5 rounded-[4px] transition-colors duration-[120ms] disabled:opacity-50 hover:bg-[var(--ij-bg-hover)]"
      style={{
        background: activa ? 'var(--ij-bg-hover)' : 'transparent',
        borderLeft: activa ? '2px solid var(--ij-teal)' : '2px solid transparent',
        cursor: deshabilitada ? 'not-allowed' : 'pointer',
      }}
    >
      <div className="flex items-start gap-2">
        <MessageSquare
          className="h-3.5 w-3.5 mt-0.5 shrink-0"
          style={{ color: activa ? 'var(--ij-teal)' : 'var(--ij-text-muted)' }}
        />
        <div className="min-w-0 flex-1">
          <p
            className="text-xs leading-snug truncate"
            style={{ color: activa ? 'var(--ij-text-primary)' : 'var(--ij-text-default)' }}
          >
            {sesion.titulo}
          </p>
          <div className="flex items-center gap-1.5 mt-1" style={{ fontSize: '10px' }}>
            {sesion.moduloActivo && (
              <span style={{ color: 'var(--ij-text-muted)' }}>
                {NOMBRE_MODULO[sesion.moduloActivo]}
              </span>
            )}
            {sesion.moduloActivo && <span style={{ color: 'var(--ij-comment)' }}>·</span>}
            <span style={{ color: 'var(--ij-comment)' }}>{haceCuanto(sesion.actualizada)}</span>
          </div>
        </div>
      </div>
    </button>
  )
}
