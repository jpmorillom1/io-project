import { useNavigate } from 'react-router'
import { Badge } from '@/components/ui/badge'

const MODULOS = [
  { id: 'lp-simplex', label: 'LP Simplex', desc: 'Programación Lineal', disponible: true, path: '/lp' },
  { id: 'transporte', label: 'Transporte', desc: 'Asignación y transporte', disponible: false, path: '' },
  { id: 'redes', label: 'Redes', desc: 'Rutas y flujos', disponible: false, path: '' },
  { id: 'pl-entera', label: 'PL Entera', desc: 'Branch & Bound', disponible: false, path: '' },
  { id: 'prog-din', label: 'Prog. Dinámica', desc: 'Optimización por etapas', disponible: false, path: '' },
  { id: 'inventario', label: 'Inventarios', desc: 'EOQ y variantes', disponible: false, path: '' },
]

export function Home() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen p-8" style={{ background: 'var(--ij-bg-secondary)' }}>
      <div className="max-w-4xl mx-auto">
        <h1
          className="font-semibold mb-1"
          style={{ fontSize: '20px', lineHeight: '24px', color: 'var(--ij-text-primary)' }}
        >
          Plataforma IO
        </h1>
        <p className="mb-8" style={{ fontSize: '13px', color: 'var(--ij-text-secondary)' }}>
          Selecciona un módulo para comenzar
        </p>
        <div className="grid grid-cols-3 gap-4">
          {MODULOS.map(m => (
            <ModuloCard key={m.id} modulo={m} onClick={() => m.disponible && navigate(m.path)} />
          ))}
        </div>
      </div>
    </div>
  )
}

interface ModuloCardProps {
  modulo: typeof MODULOS[number]
  onClick: () => void
}

function ModuloCard({ modulo: m, onClick }: ModuloCardProps) {
  return (
    <div
      className={`rounded-[4px] p-5 transition-colors duration-[120ms] ${
        m.disponible ? 'cursor-pointer' : 'opacity-60 cursor-not-allowed'
      }`}
      style={{
        background: 'var(--ij-bg-editor)',
        borderLeft: m.id === 'lp-simplex' ? '2px solid var(--ij-teal)' : undefined,
      }}
      onMouseEnter={e => {
        if (m.disponible)
          (e.currentTarget as HTMLElement).style.background = 'var(--ij-bg-hover)'
      }}
      onMouseLeave={e => {
        (e.currentTarget as HTMLElement).style.background = 'var(--ij-bg-secondary)'
      }}
      onClick={onClick}
    >
      <p className="font-semibold text-sm" style={{ color: 'var(--ij-text-primary)' }}>
        {m.label}
      </p>
      <p className="text-xs mt-0.5" style={{ color: 'var(--ij-text-secondary)' }}>
        {m.desc}
      </p>
      {!m.disponible && (
        <Badge variant="secondary" className="mt-2 text-xs">
          Próximamente
        </Badge>
      )}
    </div>
  )
}
