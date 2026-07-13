import { useNavigate } from 'react-router'

export function Logo() {
  const navigate = useNavigate()

  return (
    // El hover se filtra con `hover:hover`: en táctil un tap dispara un hover falso
    // y el logo se quedaría agrandado después de navegar.
    // El SVG va inline (no <img>) para poder pintarlo con `currentColor` y que
    // combine con el resto de íconos del toolbar en vez de perderse en negro.
    <button
      type="button"
      onClick={() => navigate('/')}
      title="Ir al Inicio (Pivot IO Studio)"
      aria-label="Ir al Inicio"
      className="h-6 w-6 rounded-[6px] flex items-center justify-center select-none shrink-0 cursor-pointer p-0 border-0 transition-transform duration-[120ms] ease-out active:scale-95 [@media(hover:hover)]:hover:scale-105"
      style={{ color: 'var(--ij-text-secondary)' }}
    >
      <svg viewBox="0 0 500 500" width="100%" height="100%" className="h-full w-full">
        <defs>
          <mask id="pivot-logo-inner-holes">
            <rect width="100%" height="100%" fill="white" />
            <circle cx="180" cy="340" r="52" fill="black" />
            <circle cx="180" cy="140" r="38" fill="black" />
            <circle cx="350" cy="240" r="38" fill="black" />
          </mask>
        </defs>
        <g mask="url(#pivot-logo-inner-holes)">
          <line x1="180" y1="340" x2="180" y2="140" stroke="currentColor" strokeWidth="32" strokeLinecap="round" />
          <line x1="180" y1="340" x2="350" y2="240" stroke="currentColor" strokeWidth="32" strokeLinecap="round" />
          <circle cx="180" cy="340" r="95" fill="currentColor" />
          <circle cx="180" cy="140" r="72" fill="currentColor" />
          <circle cx="350" cy="240" r="72" fill="currentColor" />
        </g>
      </svg>
    </button>
  )
}
