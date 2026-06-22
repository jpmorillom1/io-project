import { useSugerirModelo } from '@/hooks'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Loader2, Sparkles } from 'lucide-react'

export function ProblemInput() {
  const descripcionProblema = useWorkspaceStore(s => s.descripcionProblema)
  const setDescripcion = useWorkspaceStore(s => s.setDescripcion)
  const { sugerir, isSuggesting, advertencias, error } = useSugerirModelo()

  return (
    <div className="space-y-2">
      <Textarea
        value={descripcionProblema}
        onChange={e => setDescripcion(e.target.value)}
        placeholder="Describe tu problema de programación lineal en lenguaje natural..."
        className="min-h-[100px]"
      />
      {advertencias.length > 0 && (
        <ul className="space-y-0.5">
          {advertencias.map((a, i) => (
            <li key={i} className="text-xs" style={{ color: 'var(--ij-amber)' }}>
              ⚠ {a}
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>
      )}
      <div className="flex justify-end">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => sugerir(descripcionProblema)}
          disabled={isSuggesting || !descripcionProblema.trim()}
        >
          {isSuggesting ? (
            <>
              <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              Analizando...
            </>
          ) : (
            <>
              <Sparkles className="h-3.5 w-3.5 mr-1.5" />
              Sugerir modelo
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
