interface Props {
  errores: string[]
  sugerencias?: string[]
}

export function ValidationFeedback({ errores, sugerencias = [] }: Props) {
  if (errores.length === 0) return null
  return (
    <div className="space-y-1">
      <p className="text-xs font-semibold" style={{ color: 'var(--ij-red)' }}>
        Errores encontrados:
      </p>
      <ul className="list-disc list-inside space-y-0.5">
        {errores.map((e, i) => (
          <li key={i} className="text-xs" style={{ color: 'var(--ij-red)' }}>
            {e}
          </li>
        ))}
      </ul>
      {sugerencias.length > 0 && (
        <>
          <p className="text-xs font-semibold pt-1" style={{ color: 'var(--ij-amber)' }}>
            Sugerencias:
          </p>
          <ul className="list-disc list-inside space-y-0.5">
            {sugerencias.map((s, i) => (
              <li key={i} className="text-xs" style={{ color: 'var(--ij-amber)' }}>
                {s}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
