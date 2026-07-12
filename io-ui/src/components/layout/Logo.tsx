import { useNavigate } from 'react-router'

export function Logo() {
  const navigate = useNavigate()

  return (
    // El hover se filtra con `hover:hover`: en táctil un tap dispara un hover falso
    // y el logo se quedaría agrandado después de navegar.
    <button
      type="button"
      onClick={() => navigate('/')}
      title="Ir al Inicio (Pivot IO Studio)"
      aria-label="Ir al Inicio"
      className="h-6 w-6 rounded-[6px] flex items-center justify-center select-none shrink-0 cursor-pointer p-0 border-0 transition-transform duration-[120ms] ease-out active:scale-95 [@media(hover:hover)]:hover:scale-105"
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
    </button>
  )
}
