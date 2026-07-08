import { useEffect, type CSSProperties } from 'react'
import { useEnteraForm, aModeloEntero } from '@/hooks/entera/useEnteraForm'
import { useResolverEntera } from '@/hooks/entera/useResolverEntera'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Trash2, Plus, Loader2, BotMessageSquare } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { TipoObjetivo, TipoRestriccion, TipoVariable } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

const TIPO_VAR_COLOR: Record<TipoVariable, string> = {
  ENTERA: 'var(--ij-teal)',
  BINARIA: 'var(--ij-amber)',
  CONTINUA: 'var(--ij-text-secondary)',
}

/**
 * Editor del modelo de PL Entera. Espeja ModelEditor (LP) para la relajación
 * —objetivo + restricciones ≤/≥/=— y añade una sección de variables con su tipo
 * de integralidad (ENTERA / BINARIA / CONTINUA). BINARIA fuerza x∈{0,1}: el solver
 * añade x≤1 automáticamente.
 */
export function EnteraModelEditor() {
  const form = useEnteraForm()
  const { resolver, isSolving, error } = useResolverEntera()
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)
  const modeloStore = useWorkspaceStore(s => s.modeloEntero)

  const { modelo } = form
  const disabled = isChatBusy || isSolving

  // Cuando el chat propone/aprueba un problema entero, refleja el modelo aquí.
  useEffect(() => {
    if (modeloStore) form.reemplazar(modeloStore)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeloStore])

  function numInput(value: number, onChange: (v: number) => void) {
    return (
      <input
        type="number"
        value={value === 0 ? '' : value}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-16 h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color: 'var(--ij-cyan)', border: '1px solid var(--ij-border)', outline: 'none' }}
      />
    )
  }

  const labelSeccion = (texto: string) => (
    <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--ij-text-secondary)' }}>
      {texto}
    </p>
  )

  return (
    <div className="space-y-4">
      {ultimaActualizacionIA && !isChatBusy && (
        <ShaderGlow target="banner" state="done">
          <BotMessageSquare className="h-3.5 w-3.5 shrink-0" />
          <span>
            {ultimaActualizacionIA === 'modelo' && 'Pivot completó el modelo sugerido ✓'}
            {ultimaActualizacionIA === 'validacion' && 'Pivot revisó el modelo ✓'}
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa el árbol ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — el modelo se actualizará automáticamente
        </ShaderGlow>
      )}

      {/* Objetivo */}
      <div>
        {labelSeccion('Objetivo')}
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
              {j > 0 && <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>+</span>}
              {numInput(c, v => form.setCoeficienteObjetivo(j, v))}
              <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>·</span>
              <span className="w-10 text-center" style={{ ...MONO, color: 'var(--ij-purple)' }}>{modelo.variables[j]}</span>
            </span>
          ))}
        </div>
      </div>

      {/* Restricciones */}
      <div>
        {labelSeccion('Restricciones')}
        <div className="space-y-2">
          {modelo.restricciones.map((r, i) => (
            <div key={i} className="flex items-center gap-2 flex-wrap">
              {r.coeficientes.map((c, j) => (
                <span key={j} className="flex items-center gap-1">
                  {j > 0 && <span style={{ color: 'var(--ij-text-secondary)', ...MONO }}>+</span>}
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
              <button
                onClick={() => form.quitarRestriccion(i)}
                disabled={disabled || modelo.restricciones.length <= 1}
                title="Quitar restricción"
                className="p-1 disabled:opacity-30 disabled:cursor-not-allowed"
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ij-text-secondary)' }}
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
          {modelo.variables.length > 1 && (
            <Button variant="ghost" size="sm" disabled={disabled} onClick={() => form.quitarVariable(modelo.variables.length - 1)}>
              <Trash2 className="h-3.5 w-3.5 mr-1" />
              Variable
            </Button>
          )}
        </div>
      </div>

      {/* Variables · tipo de integralidad */}
      <div>
        {labelSeccion('Variables · tipo de integralidad')}
        <div className="flex flex-wrap gap-3">
          {modelo.variables.map((nombre, i) => (
            <div
              key={i}
              className="flex items-center gap-2 rounded-[4px] px-2 py-1.5"
              style={{ background: 'var(--ij-bg-secondary)', border: '1px solid var(--ij-border)' }}
            >
              <input
                type="text"
                value={nombre}
                disabled={disabled}
                onChange={e => form.setNombreVariable(i, e.target.value)}
                className="w-12 h-7 text-center rounded-[4px] disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ ...MONO, background: 'var(--ij-bg-editor)', color: 'var(--ij-purple)', border: '1px solid var(--ij-border)', outline: 'none' }}
              />
              <select
                value={modelo.tiposVariable[i]}
                disabled={disabled}
                onChange={e => form.setTipoVariable(i, e.target.value as TipoVariable)}
                className="h-7 rounded-[4px] px-1.5 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
                style={{ ...MONO, fontSize: '12px', color: TIPO_VAR_COLOR[modelo.tiposVariable[i]] }}
              >
                <option value="ENTERA">Entera</option>
                <option value="BINARIA">Binaria</option>
                <option value="CONTINUA">Continua</option>
              </select>
            </div>
          ))}
        </div>
        <p className="text-xs mt-2" style={{ color: 'var(--ij-text-secondary)' }}>
          <span style={{ color: 'var(--ij-amber)' }}>Binaria</span> fuerza x∈{'{0,1}'} ·{' '}
          <span style={{ color: 'var(--ij-text-secondary)' }}>Continua</span> deja la variable relajada (modelo mixto MILP)
        </p>
      </div>

      {error && <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>}

      <div className="flex gap-2 justify-end">
        <Button onClick={() => resolver(aModeloEntero(modelo))} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver (Branch &amp; Bound) →
        </Button>
      </div>
    </div>
  )
}
