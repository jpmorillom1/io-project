import { useLocation, useNavigate } from 'react-router'
import { Sigma, Truck, Network, Binary, Workflow, Package, type LucideIcon } from 'lucide-react'

interface Modulo {
  id: string
  label: string
  icon: LucideIcon
  path: string
  disponible: boolean
}

const MODULOS: Modulo[] = [
  { id: 'lp', label: 'Programación Lineal', icon: Sigma, path: '/lp', disponible: true },
  { id: 'transporte', label: 'Transporte', icon: Truck, path: '/transporte', disponible: true },
  { id: 'redes', label: 'Redes', icon: Network, path: '/redes', disponible: true },
  { id: 'pl-entera', label: 'PL Entera', icon: Binary, path: '/pl-entera', disponible: true },
  { id: 'prog-din', label: 'Programación Dinámica', icon: Workflow, path: '/prog-din', disponible: false },
  { id: 'inventario', label: 'Inventarios', icon: Package, path: '/inventario', disponible: false },
]

export function ModuleRail() {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <div
      className="flex flex-col items-center gap-1 py-2 shrink-0"
      style={{ width: '44px' }}
    >
      {MODULOS.map(m => {
        const activo = m.disponible && location.pathname.startsWith(m.path)
        return (
          <button
            key={m.id}
            title={m.disponible ? m.label : `${m.label} (próximamente)`}
            disabled={!m.disponible}
            onClick={() => m.disponible && navigate(m.path)}
            className="flex items-center justify-center h-8 w-8 rounded-[4px] transition-colors duration-[120ms] disabled:cursor-not-allowed"
            style={{
              background: activo ? 'var(--ij-bg-hover)' : 'transparent',
              borderLeft: activo ? '2px solid var(--ij-teal)' : '2px solid transparent',
              borderTop: 'none',
              borderRight: 'none',
              borderBottom: 'none',
              color: activo ? 'var(--ij-teal)' : 'var(--ij-text-secondary)',
              opacity: m.disponible ? 1 : 0.35,
              cursor: m.disponible ? 'pointer' : 'not-allowed',
            }}
            onMouseEnter={e => {
              if (!activo && m.disponible) (e.currentTarget as HTMLElement).style.background = 'var(--ij-bg-hover)'
            }}
            onMouseLeave={e => {
              if (!activo && m.disponible) (e.currentTarget as HTMLElement).style.background = 'transparent'
            }}
          >
            <m.icon className="h-[18px] w-[18px]" />
          </button>
        )
      })}
    </div>
  )
}
