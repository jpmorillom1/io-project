import { useNavigate } from 'react-router'
import { Card, CardContent } from '@/components/ui/card'
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
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-4xl mx-auto">
        <h1 className="text-2xl font-bold text-slate-800 mb-1">Plataforma IO</h1>
        <p className="text-slate-500 text-sm mb-8">Selecciona un módulo para comenzar</p>
        <div className="grid grid-cols-3 gap-4">
          {MODULOS.map(m => (
            <Card
              key={m.id}
              className={`cursor-pointer transition-all ${
                m.disponible
                  ? 'hover:shadow-md hover:border-blue-300 border-slate-200'
                  : 'opacity-60 cursor-not-allowed border-slate-100'
              }`}
              onClick={() => m.disponible && navigate(m.path)}
            >
              <CardContent className="p-5">
                <p className="font-semibold text-slate-800 text-sm">{m.label}</p>
                <p className="text-xs text-slate-500 mt-0.5">{m.desc}</p>
                {!m.disponible && (
                  <Badge variant="secondary" className="mt-2 text-xs">
                    Próximamente
                  </Badge>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  )
}
