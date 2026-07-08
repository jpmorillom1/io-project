import { useEffect, type CSSProperties } from 'react'
import { useInventarioForm, aModeloInventario } from '@/hooks/inventario/useInventarioForm'
import { useResolverInventario } from '@/hooks/inventario/useResolverInventario'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Trash2, Plus, Loader2, BotMessageSquare } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { MetodoInventario } from '@/types/io'
import type { InventarioFormModelo } from '@/hooks/inventario/useInventarioForm'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

const METODO_HINT: Record<MetodoInventario, string> = {
  EOQ_BASICO: 'EOQ de Wilson: demanda constante, sin faltantes; Q* = √(2·D·K / H)',
  PRODUCCION_ECONOMICA: 'POQ/EPQ: reposición gradual a tasa P > D; el inventario nunca llega a Q*',
  EOQ_FALTANTES: 'EOQ con faltantes planificados (backorders): se admite escasez con costo b',
  PUNTO_REORDEN: 'EOQ + punto de reorden R = d·L; avisa cuándo relanzar el pedido',
  EOQ_DESCUENTOS: 'Descuentos por cantidad (all-units): elige el tramo de menor costo total',
}

// Campos numéricos escalares que aplican a cada submodelo (los tramos van aparte).
type CampoNum = Exclude<keyof InventarioFormModelo, 'metodo' | 'tramos'>

const CAMPOS_POR_METODO: Record<MetodoInventario, CampoNum[]> = {
  EOQ_BASICO: ['demanda', 'costoOrden', 'costoMantener'],
  PRODUCCION_ECONOMICA: ['demanda', 'costoOrden', 'costoMantener', 'tasaProduccion'],
  EOQ_FALTANTES: ['demanda', 'costoOrden', 'costoMantener', 'costoFaltante'],
  PUNTO_REORDEN: ['demanda', 'costoOrden', 'costoMantener', 'leadTimeDias', 'diasHabiles'],
  EOQ_DESCUENTOS: ['demanda', 'costoOrden', 'tasaMantenerPorcentaje'],
}

const CAMPO_LABEL: Record<CampoNum, string> = {
  demanda: 'Demanda anual (D)',
  costoOrden: 'Costo de ordenar (K)',
  costoMantener: 'Costo de mantener (H)',
  costoFaltante: 'Costo de faltante (b)',
  tasaProduccion: 'Tasa de producción (P)',
  leadTimeDias: 'Lead time (días, L)',
  diasHabiles: 'Días hábiles / año',
  tasaMantenerPorcentaje: 'Tasa de mantener (i, fracción del precio)',
}

/**
 * Editor del modelo de Inventarios. Espeja RedModelEditor: banners de Pivot,
 * selector de submodelo, campos numéricos que cambian según el método y, para
 * EOQ con descuentos, la tabla de tramos precio-cantidad.
 */
export function InventarioModelEditor() {
  const form = useInventarioForm()
  const { resolver, isSolving, error } = useResolverInventario()
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)
  const modeloStore = useWorkspaceStore(s => s.modeloInventario)

  const { modelo } = form
  const disabled = isChatBusy || isSolving
  const esDescuentos = modelo.metodo === 'EOQ_DESCUENTOS'

  // Cuando el chat propone/aprueba un problema de inventario, refleja el modelo aquí.
  useEffect(() => {
    if (modeloStore) form.reemplazar(modeloStore)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeloStore])

  function numInput(value: number, onChange: (v: number) => void, step = 'any') {
    return (
      <input
        type="number"
        step={step}
        value={value === 0 ? '' : value}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-24 h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
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
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa el desarrollo ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — el modelo se actualizará automáticamente
        </ShaderGlow>
      )}

      {/* Submodelo */}
      <div>
        {labelSeccion('Submodelo')}
        <select
          value={modelo.metodo}
          disabled={disabled}
          onChange={e => form.setMetodo(e.target.value as MetodoInventario)}
          className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
          style={{ ...MONO, color: 'var(--ij-orange)' }}
        >
          <option value="EOQ_BASICO">EOQ básico (Wilson)</option>
          <option value="PRODUCCION_ECONOMICA">Producción económica (POQ/EPQ)</option>
          <option value="EOQ_FALTANTES">EOQ con faltantes</option>
          <option value="PUNTO_REORDEN">Punto de reorden</option>
          <option value="EOQ_DESCUENTOS">EOQ con descuentos por cantidad</option>
        </select>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          {METODO_HINT[modelo.metodo]}
        </p>
      </div>

      {/* Parámetros escalares del submodelo */}
      <div>
        {labelSeccion('Parámetros')}
        <div className="flex flex-wrap gap-4">
          {CAMPOS_POR_METODO[modelo.metodo].map(campo => (
            <div key={campo} className="flex flex-col gap-1">
              <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>{CAMPO_LABEL[campo]}</span>
              {numInput(modelo[campo], v => form.setCampo(campo, v))}
            </div>
          ))}
        </div>
      </div>

      {/* Tramos (solo EOQ con descuentos) */}
      {esDescuentos && (
        <div>
          {labelSeccion('Tramos de precio · cantidad mínima → precio unitario')}
          <div className="overflow-x-auto">
            <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
              <thead>
                <tr style={{ ...MONO, fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
                  <th className="font-semibold">Cantidad mínima</th>
                  <th className="font-semibold">Precio unitario</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {modelo.tramos.map((t, i) => (
                  <tr key={i}>
                    <td>{numInput(t.cantidadMinima, v => form.setTramo(i, { cantidadMinima: v }))}</td>
                    <td>{numInput(t.precioUnitario, v => form.setTramo(i, { precioUnitario: v }))}</td>
                    <td>
                      <button
                        disabled={disabled || modelo.tramos.length <= 1}
                        onClick={() => form.quitarTramo(i)}
                        title="Quitar tramo"
                        className="disabled:opacity-30 disabled:cursor-not-allowed"
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ij-text-muted)', padding: 0 }}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarTramo} className="mt-1">
            <Plus className="h-3.5 w-3.5 mr-1" />
            Tramo
          </Button>
          <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
            H se calcula como i · precio de cada tramo. El primer tramo suele arrancar en cantidad mínima 0.
          </p>
        </div>
      )}

      {error && <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>}

      <div className="flex gap-2 justify-end">
        <Button onClick={() => resolver(modelo.metodo, aModeloInventario(modelo))} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver →
        </Button>
      </div>
    </div>
  )
}
