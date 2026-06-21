interface Props {
  errores: string[]
  sugerencias?: string[]
}

export function ValidationFeedback({ errores, sugerencias = [] }: Props) {
  if (errores.length === 0) return null
  return (
    <div className="rounded-md border border-red-200 bg-red-50 p-3 space-y-1">
      <p className="text-xs font-semibold text-red-700">Errores encontrados:</p>
      <ul className="list-disc list-inside space-y-0.5">
        {errores.map((e, i) => (
          <li key={i} className="text-xs text-red-600">{e}</li>
        ))}
      </ul>
      {sugerencias.length > 0 && (
        <>
          <p className="text-xs font-semibold text-amber-700 pt-1">Sugerencias:</p>
          <ul className="list-disc list-inside space-y-0.5">
            {sugerencias.map((s, i) => (
              <li key={i} className="text-xs text-amber-600">{s}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
