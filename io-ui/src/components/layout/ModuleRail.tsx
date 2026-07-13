import { useLocation, useNavigate } from 'react-router'
import { Home, Sigma, Truck, Network, Binary, Workflow, Package, type LucideIcon } from 'lucide-react'
import { AnimatedBackground } from '@/components/motion-primitives/animated-background'
import { cn } from '@/lib/utils'

interface Modulo {
  id: string
  label: string
  icon: LucideIcon
  path: string
  disponible: boolean
}

const MODULOS: Modulo[] = [
  { id: 'home', label: 'Inicio', icon: Home, path: '/', disponible: true },
  { id: 'lp', label: 'Programación Lineal', icon: Sigma, path: '/lp', disponible: true },
  { id: 'transporte', label: 'Transporte', icon: Truck, path: '/transporte', disponible: true },
  { id: 'redes', label: 'Redes', icon: Network, path: '/redes', disponible: true },
  { id: 'pl-entera', label: 'PL Entera', icon: Binary, path: '/pl-entera', disponible: true },
  { id: 'prog-din', label: 'Programación Dinámica', icon: Workflow, path: '/dinamica', disponible: true },
  { id: 'inventario', label: 'Inventarios', icon: Package, path: '/inventario', disponible: true },
]

function esActivo(m: Modulo, pathname: string): boolean {
  if (!m.disponible) return false
  return m.path === '/'
    ? pathname === '/' || pathname === '/home'
    : pathname.startsWith(m.path)
}

export function ModuleRail() {
  const location = useLocation()
  const navigate = useNavigate()

  const moduloActivo = MODULOS.find(m => esActivo(m, location.pathname))

  // `AnimatedBackground` sobrescribe el onClick de cada hijo, así que la
  // navegación va aquí y no en el botón.
  function alSeleccionar(id: string | null) {
    const m = MODULOS.find(x => x.id === id)
    if (m?.disponible && m.path !== location.pathname) navigate(m.path)
  }

  return (
    <div className="flex flex-col items-center gap-1 py-2 shrink-0" style={{ width: '44px' }}>
      <AnimatedBackground
        defaultValue={moduloActivo?.id}
        onValueChange={alSeleccionar}
        // El resaltado ya no vive en el botón: es este bloque el que se desplaza
        // de un módulo a otro, arrastrando consigo el borde teal.
        className="rounded-[4px] bg-[var(--ij-bg-hover)] border-l-2 border-l-[var(--ij-teal)]"
        transition={{ type: 'spring', bounce: 0.2, duration: 0.3 }}
      >
        {MODULOS.map(m => {
          const activo = m.id === moduloActivo?.id
          return (
            <button
              key={m.id}
              data-id={m.id}
              type="button"
              title={m.disponible ? m.label : `${m.label} (próximamente)`}
              disabled={!m.disponible}
              // El hover va por CSS, no por handlers: al mudarse el fondo al div
              // que se desliza, `style.background` es constante y React ya no
              // reescribiría una mutación imperativa (se quedaría pegada).
              className={cn(
                'flex items-center justify-center h-8 w-8 rounded-[4px] transition-colors duration-[120ms] disabled:cursor-not-allowed',
                !activo && m.disponible && 'hover:bg-[var(--ij-bg-hover)]'
              )}
              // `background` NO puede ir aquí: un estilo inline vence a la clase
              // `hover:` de Tailwind y el resaltado al pasar el ratón no se veria.
              style={{
                color: activo ? 'var(--ij-teal)' : 'var(--ij-text-secondary)',
                opacity: m.disponible ? 1 : 0.35,
                cursor: m.disponible ? 'pointer' : 'not-allowed',
                border: 'none',
              }}
            >
              <m.icon className="h-[18px] w-[18px]" />
            </button>
          )
        })}
      </AnimatedBackground>
    </div>
  )
}
