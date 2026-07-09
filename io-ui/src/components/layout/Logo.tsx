import { useNavigate } from 'react-router'

export function Logo() {
  const navigate = useNavigate()

  return (
    <div
      onClick={() => navigate('/')}
      title="Ir al Inicio (Pivot IO Studio)"
      className="h-6 w-6 rounded-[6px] flex items-center justify-center select-none shrink-0 cursor-pointer transition-transform hover:scale-105"
      style={{
        background: 'linear-gradient(135deg, #8C33EB 0%, #24C2D6 55%, #F2801A 100%)',
        color: '#0B1211',
        fontSize: '10px',
        fontWeight: 700,
        fontFamily: "'JetBrains Mono', monospace",
        letterSpacing: '-0.02em',
      }}
    >
      P
    </div>
  )
}
