import { useState, type CSSProperties } from 'react'
import { useModeloForm, useValidarModelo, useSimplex, useGrafico } from '@/hooks'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { ValidationFeedback } from './ValidationFeedback'
import { Trash2, Plus, Loader2, BotMessageSquare } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { TipoObjetivo, TipoRestriccion } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

export function ModelEditor() {
  const form = useModeloForm()
  const { validar, isValidating, errores, sugerencias } = useValidarModelo()
  const { resolver, isSolving, error: errorSolver } = useSimplex()
  const { resolver: graficar, isSolving: isGraficando, error: errorGrafico } = useGrafico()
  const descripcionProblema = useWorkspaceStore(s => s.descripcionProblema)
  const status = useWorkspaceStore(s => s.status)
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)

  const modelo = form.modelo
  const disabled = isChatBusy || isSolving || isGraficando || isValidating
  const puedeGraficar = modelo.variables.length === 2

  function numInput(value: number, onChange: (v: number) => void) {
    return (
      <input
        type="number"
        value={value === 0 ? '' : value}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-16 h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
        style={{
          ...MONO,
          background: 'var(--ij-bg-secondary)',
          color: 'var(--ij-cyan)',
          border: '1px solid var(--ij-border)',
          outline: 'none',
        }}
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
      {ultimaActualizacionIA && !isChatBusy && (
        <ShaderGlow target="banner" state="done">
          <BotMessageSquare className="h-3.5 w-3.5 shrink-0" />
          <span>
            {ultimaActualizacionIA === 'modelo' && 'Pivot completó el formulario con el modelo sugerido ✓'}
            {ultimaActualizacionIA === 'validacion' && 'Pivot validó el modelo ✓'}
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa el tableau ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — el formulario se actualizará automáticamente
        </ShaderGlow>
      )}

      <div>
        <p
          className="text-xs font-semibold uppercase tracking-wider mb-2"
          style={{ color: 'var(--ij-text-secondary)' }}
        >
          Objetivo
        </p>
        <div className="flex items-center gap-2 flex-wrap">
          <select
            value={modelo.objetivo.tipo}
            disabled={disabled}
            onChange={e => form.setTipoObjetivo(e.target.value as TipoObjetivo)}
            className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
            style={{ ...MONO, color: 'var(--ij-orange)' }}
          >
            <option value="MAXIMIZAR">MAX</option>
            <option value="MINIMIZAR">MIN</option>
          </select>
          {modelo.objetivo.coeficientes.map((c, j) => (
            <span key={j} className="flex items-center gap-1">
              {j > 0 && (
                <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>+</span>
              )}
              {numInput(c, v => form.setCoeficienteObjetivo(j, v))}
              <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>·</span>
              <input
                type="text"
                value={modelo.variables[j]}
                disabled={disabled}
                onChange={e => form.setNombreVariable(j, e.target.value)}
                className="w-10 h-8 text-center rounded-[4px] disabled:opacity-50 disabled:cursor-not-allowed"
                style={{
                  ...MONO,
                  background: 'var(--ij-bg-secondary)',
                  color: 'var(--ij-purple)',
                  border: '1px solid var(--ij-border)',
                  outline: 'none',
                }}
              />
            </span>
          ))}
        </div>
      </div>

      <div>
        <p
          className="text-xs font-semibold uppercase tracking-wider mb-2"
          style={{ color: 'var(--ij-text-secondary)' }}
        >
          Restricciones
        </p>
        <div className="space-y-2">
          {modelo.restricciones.map((r, i) => (
            <div key={i} className="flex items-center gap-2 flex-wrap">
              {r.coeficientes.map((c, j) => (
                <span key={j} className="flex items-center gap-1">
                  {j > 0 && (
                    <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>+</span>
                  )}
                  {numInput(c, v => form.setCoeficienteRestriccion(i, j, v))}
                  <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>
                    ·<span style={{ color: 'var(--ij-purple)' }}>{modelo.variables[j]}</span>
                  </span>
                </span>
              ))}
              <select
                value={r.tipo}
                disabled={disabled}
                onChange={e => form.setTipoRestriccion(i, e.target.value as TipoRestriccion)}
                className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ ...MONO, color: 'var(--ij-text-secondary)' }}
              >
                <option value="LEQ">≤</option>
                <option value="GEQ">≥</option>
                <option value="EQ">=</option>
              </select>
              {numInput(r.rhs, v => form.setRhs(i, v))}
              <TrashButton
                onClick={() => form.quitarRestriccion(i)}
                disabled={disabled || modelo.restricciones.length <= 1}
              />
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
      {errorSolver && (
        <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{errorSolver}</p>
      )}
      {errorGrafico && (
        <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{errorGrafico}</p>
      )}

      <div className="flex gap-2 justify-end">
        <Button
          variant="outline"
          onClick={handleValidar}
          disabled={disabled || status === 'SOLVING'}
        >
          {isValidating && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Validar
        </Button>
        <Button
          variant="outline"
          onClick={() => graficar(modelo)}
          disabled={disabled || !puedeGraficar}
          title={puedeGraficar ? undefined : 'Solo disponible con exactamente 2 variables'}
        >
          {isGraficando && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Graficar
        </Button>
        <Button onClick={() => resolver(modelo)} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver →
        </Button>
      </div>
    </div>
  )
}

function TrashButton({ onClick, disabled }: { onClick: () => void; disabled: boolean }) {
  const [hovered, setHovered] = useState(false)
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="p-1 disabled:opacity-30 disabled:cursor-not-allowed transition-colors duration-[120ms]"
      style={{
        color: hovered && !disabled ? 'var(--ij-red)' : 'var(--ij-text-secondary)',
        background: 'none',
        border: 'none',
        cursor: disabled ? 'not-allowed' : 'pointer',
      }}
      onMouseEnter={() => !disabled && setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <Trash2 className="h-4 w-4" />
    </button>
  )
}
