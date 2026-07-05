import { useEffect, type CSSProperties } from 'react'
import { useTransporteForm } from '@/hooks/transporte/useTransporteForm'
import { useResolverTransporte } from '@/hooks/transporte/useResolverTransporte'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Trash2, Plus, Loader2, BotMessageSquare } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { MetodoTransporte } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

const METODO_HINT: Record<MetodoTransporte, string> = {
  MODI: 'MODI compara las 3 soluciones iniciales y optimiza hasta el óptimo',
  ESQUINA_NOROESTE: 'Solución básica inicial por esquina noroeste (no necesariamente óptima)',
  COSTO_MINIMO: 'Solución básica inicial por costo mínimo (no necesariamente óptima)',
  VOGEL: 'Solución básica inicial por Vogel/VAM (no necesariamente óptima)',
}

/**
 * Editor de la tabla de transporte (oferta, demanda, matriz de costos).
 * Espeja el estilo de ModelEditor (LP): banners de Pivot, labels en versalitas,
 * inputs monoespaciados y botones consistentes.
 */
export function TransporteModelEditor() {
  const form = useTransporteForm()
  const { resolver, isSolving, error } = useResolverTransporte()
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)
  const modeloStore = useWorkspaceStore(s => s.modeloTransporte)
  const setModeloGrafico = useWorkspaceStore(s => s.setModeloTransporteGrafico)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  const { modelo, totales } = form
  const disabled = isChatBusy || isSolving

  // Cuando el chat propone/aprueba un problema de transporte, refleja el modelo aquí.
  useEffect(() => {
    if (modeloStore) form.reemplazar(modeloStore)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeloStore])

  function numInput(value: number, onChange: (v: number) => void, color = 'var(--ij-cyan)') {
    return (
      <input
        type="number"
        value={value === 0 ? '' : value}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-16 h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color, border: '1px solid var(--ij-border)', outline: 'none' }}
      />
    )
  }

  function nameInput(value: string, onChange: (v: string) => void, color: string) {
    return (
      <input
        type="text"
        value={value}
        disabled={disabled}
        onChange={e => onChange(e.target.value)}
        className="w-20 h-8 text-center rounded-[4px] disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color, border: '1px solid var(--ij-border)', outline: 'none' }}
      />
    )
  }

  return (
    <div className="space-y-4">
      {ultimaActualizacionIA && !isChatBusy && (
        <ShaderGlow target="banner" state="done">
          <BotMessageSquare className="h-3.5 w-3.5 shrink-0" />
          <span>
            {ultimaActualizacionIA === 'modelo' && 'Pivot completó la tabla con el modelo sugerido ✓'}
            {ultimaActualizacionIA === 'validacion' && 'Pivot revisó el modelo ✓'}
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa la tabla ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — la tabla se actualizará automáticamente
        </ShaderGlow>
      )}

      {/* Método */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--ij-text-secondary)' }}>
          Método
        </p>
        <select
          value={modelo.metodo}
          disabled={disabled}
          onChange={e => form.setMetodo(e.target.value as MetodoTransporte)}
          className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
          style={{ ...MONO, color: 'var(--ij-orange)' }}
        >
          <option value="MODI">MODI (óptimo)</option>
          <option value="ESQUINA_NOROESTE">Esquina Noroeste</option>
          <option value="COSTO_MINIMO">Costo Mínimo</option>
          <option value="VOGEL">Vogel (VAM)</option>
        </select>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          {METODO_HINT[modelo.metodo]}
        </p>
      </div>

      {/* Tabla de costos */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--ij-text-secondary)' }}>
          Costos unitarios · Oferta · Demanda
        </p>
        <div className="overflow-x-auto">
          <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
            <thead>
              <tr>
                <th />
                {modelo.destinos.map((d, j) => (
                  <th key={j}>{nameInput(d, v => form.setNombreDestino(j, v), 'var(--ij-purple)')}</th>
                ))}
                <th className="text-xs font-semibold" style={{ color: 'var(--ij-orange)', ...MONO }}>Oferta</th>
              </tr>
            </thead>
            <tbody>
              {modelo.origenes.map((o, i) => (
                <tr key={i}>
                  <td>{nameInput(o, v => form.setNombreOrigen(i, v), 'var(--ij-purple)')}</td>
                  {modelo.destinos.map((_, j) => (
                    <td key={j}>{numInput(modelo.costos[i][j], v => form.setCelda(i, j, v))}</td>
                  ))}
                  <td>{numInput(modelo.oferta[i], v => form.setOferta(i, v), 'var(--ij-orange)')}</td>
                </tr>
              ))}
              <tr>
                <td className="text-xs font-semibold" style={{ color: 'var(--ij-teal)', ...MONO }}>Demanda</td>
                {modelo.destinos.map((_, j) => (
                  <td key={j}>{numInput(modelo.demanda[j], v => form.setDemanda(j, v), 'var(--ij-teal)')}</td>
                ))}
                <td />
              </tr>
            </tbody>
          </table>
        </div>

        <div className="mt-2 flex gap-2 flex-wrap">
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarOrigen}>
            <Plus className="h-3.5 w-3.5 mr-1" />
            Origen
          </Button>
          {modelo.origenes.length > 1 && (
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarOrigen}>
              <Trash2 className="h-3.5 w-3.5 mr-1" />
              Origen
            </Button>
          )}
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarDestino}>
            <Plus className="h-3.5 w-3.5 mr-1" />
            Destino
          </Button>
          {modelo.destinos.length > 1 && (
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarDestino}>
              <Trash2 className="h-3.5 w-3.5 mr-1" />
              Destino
            </Button>
          )}
        </div>
      </div>

      {/* Balance */}
      <p className="text-xs" style={{ color: totales.balanceado ? 'var(--ij-text-secondary)' : 'var(--ij-amber)' }}>
        Σoferta = <span style={{ color: 'var(--ij-orange)', ...MONO }}>{totales.sumaOferta}</span>
        {'  ·  '}Σdemanda = <span style={{ color: 'var(--ij-teal)', ...MONO }}>{totales.sumaDemanda}</span>
        {totales.balanceado
          ? '  — balanceado'
          : '  — se agregará un origen/destino ficticio de costo 0'}
      </p>

      {error && <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>}

      <div className="flex gap-2 justify-end">
        <Button
          variant="outline"
          onClick={() => { setModeloGrafico(modelo); setStatus('EDITING') }}
          disabled={disabled}
        >
          Graficar
        </Button>
        <Button onClick={() => resolver(modelo)} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          {modelo.metodo === 'MODI' ? 'Resolver (MODI) →' : 'Resolver →'}
        </Button>
      </div>
    </div>
  )
}
