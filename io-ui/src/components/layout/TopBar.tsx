import { CircleUserRound } from 'lucide-react'

export function TopBar() {
  return (
    <div
      className="flex-1 flex items-center justify-between px-3 min-w-0"
    >
      <p className="font-semibold truncate" style={{ fontSize: '12px', color: 'var(--ij-text-primary)' }}>
        Pivot
      </p>

      {/* Espacio reservado para cuenta / organización — próximamente */}
      <button
        type="button"
        disabled
        title="Cuenta y organización (próximamente)"
        className="flex items-center justify-center h-7 w-7 rounded-[4px] opacity-40 cursor-not-allowed shrink-0"
        style={{ color: 'var(--ij-text-secondary)', background: 'none', border: 'none' }}
      >
        <CircleUserRound className="h-4 w-4" />
      </button>
    </div>
  )
}
