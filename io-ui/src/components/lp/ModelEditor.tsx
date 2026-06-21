import { useModeloForm, useValidarModelo, useSimplex } from '@/hooks'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { ValidationFeedback } from './ValidationFeedback'
import { Trash2, Plus, Loader2, BotMessageSquare } from 'lucide-react'
import type { TipoObjetivo, TipoRestriccion } from '@/types/io'

export function ModelEditor() {
  const form = useModeloForm()
  const { validar, isValidating, errores, sugerencias } = useValidarModelo()
  const { resolver, isSolving, error: errorSolver } = useSimplex()
  const descripcionProblema = useWorkspaceStore(s => s.descripcionProblema)
  const status = useWorkspaceStore(s => s.status)
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)

  const modelo = form.modelo
  const disabled = isChatBusy || isSolving || isValidating

  function numInput(value: number, onChange: (v: number) => void) {
    return (
      <input
        type="number"
        value={value === 0 ? '' : value}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-16 h-8 text-center text-sm border border-slate-200 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
      />
    )
  }

  async function handleValidar() {
    if (!descripcionProblema.trim()) {
      alert('Escribe el enunciado del problema antes de validar.')
      return
    }
    await validar(descripcionProblema, modelo)
  }

  return (
    <div className="space-y-4">
      {/* Indicador de actualización por IA */}
      {ultimaActualizacionIA && !isChatBusy && (
        <div className="flex items-center gap-1.5 text-xs text-blue-600 bg-blue-50 border border-blue-100 rounded-md px-3 py-1.5">
          <BotMessageSquare className="h-3.5 w-3.5 shrink-0" />
          <span>
            {ultimaActualizacionIA === 'modelo' && 'Ío completó el formulario con el modelo sugerido'}
            {ultimaActualizacionIA === 'validacion' && 'Ío validó el modelo'}
            {ultimaActualizacionIA === 'resultado' && 'Ío resolvió el problema — revisa el tableau'}
          </span>
        </div>
      )}

      {isChatBusy && (
        <div className="flex items-center gap-2 text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-md px-3 py-1.5">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Ío está respondiendo — el formulario se actualizará automáticamente
        </div>
      )}

      <div>
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
          Objetivo
        </p>
        <div className="flex items-center gap-2 flex-wrap">
          <select
            value={modelo.objetivo.tipo}
            disabled={disabled}
            onChange={e => form.setTipoObjetivo(e.target.value as TipoObjetivo)}
            className="h-8 text-sm border border-slate-200 rounded-md px-2 focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <option value="MAXIMIZAR">MAX</option>
            <option value="MINIMIZAR">MIN</option>
          </select>
          {modelo.objetivo.coeficientes.map((c, j) => (
            <span key={j} className="flex items-center gap-1">
              {j > 0 && <span className="text-slate-400 text-sm">+</span>}
              {numInput(c, v => form.setCoeficienteObjetivo(j, v))}
              <span className="text-slate-500 text-sm">·</span>
              <input
                type="text"
                value={modelo.variables[j]}
                disabled={disabled}
                onChange={e => form.setNombreVariable(j, e.target.value)}
                className="w-10 h-8 text-center text-sm border border-slate-200 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white font-mono disabled:opacity-50 disabled:cursor-not-allowed"
              />
            </span>
          ))}
        </div>
      </div>

      <div>
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
          Restricciones
        </p>
        <div className="space-y-2">
          {modelo.restricciones.map((r, i) => (
            <div key={i} className="flex items-center gap-2 flex-wrap">
              {r.coeficientes.map((c, j) => (
                <span key={j} className="flex items-center gap-1">
                  {j > 0 && <span className="text-slate-400 text-sm">+</span>}
                  {numInput(c, v => form.setCoeficienteRestriccion(i, j, v))}
                  <span className="text-slate-500 text-sm">·{modelo.variables[j]}</span>
                </span>
              ))}
              <select
                value={r.tipo}
                disabled={disabled}
                onChange={e => form.setTipoRestriccion(i, e.target.value as TipoRestriccion)}
                className="h-8 text-sm border border-slate-200 rounded-md px-2 focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <option value="LEQ">≤</option>
                <option value="GEQ">≥</option>
                <option value="EQ">=</option>
              </select>
              {numInput(r.rhs, v => form.setRhs(i, v))}
              <button
                onClick={() => form.quitarRestriccion(i)}
                disabled={disabled || modelo.restricciones.length <= 1}
                className="p-1 text-slate-400 hover:text-red-500 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>
        <div className="mt-2 flex gap-2">
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarRestriccion}>
            <Plus className="h-3.5 w-3.5 mr-1" />
            Restricción
          </Button>
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarVariable}>
            <Plus className="h-3.5 w-3.5 mr-1" />
            Variable
          </Button>
          {modelo.variables.length > 2 && (
            <Button
              variant="ghost"
              size="sm"
              disabled={disabled}
              onClick={() => form.quitarVariable(modelo.variables.length - 1)}
            >
              <Trash2 className="h-3.5 w-3.5 mr-1" />
              Variable
            </Button>
          )}
        </div>
      </div>

      <ValidationFeedback errores={errores} sugerencias={sugerencias} />
      {errorSolver && <p className="text-xs text-red-600">{errorSolver}</p>}

      <div className="flex gap-2 justify-end">
        <Button
          variant="outline"
          onClick={handleValidar}
          disabled={disabled || status === 'SOLVING'}
        >
          {isValidating && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Validar
        </Button>
        <Button onClick={() => resolver(modelo)} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver →
        </Button>
      </div>
    </div>
  )
}
