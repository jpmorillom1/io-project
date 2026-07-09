import { useEffect, useState, type CSSProperties } from 'react'
import { useDinamicaForm, aModeloDinamico, sentidoConfigurable, nodosDeRuta } from '@/hooks/dinamica/useDinamicaForm'
import { useResolverDinamica } from '@/hooks/dinamica/useResolverDinamica'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Trash2, Plus, Loader2, BotMessageSquare, X, ChevronDown } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { MetodoDinamico, SentidoOptimizacion } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

const METODO_HINT: Record<MetodoDinamico, string> = {
  ASIGNACION_RECURSOS: 'Reparte un recurso total entre actividades; cada actividad da un retorno por nivel asignado',
  MOCHILA: 'Selecciona artículos (peso/valor) sin superar la capacidad; 0/1 o varias unidades',
  RUTA_ETAPAS: 'Ruta óptima en una red por etapas (problema de la diligencia)',
  PLANIFICACION_PRODUCCION: 'Plan de producción por periodos que cubre la demanda al mínimo costo',
  REEMPLAZO_EQUIPOS: 'Decidir cada año conservar o reemplazar el equipo para maximizar la ganancia neta',
}

export function DinamicaModelEditor() {
  const form = useDinamicaForm()
  const { resolver, isSolving, error } = useResolverDinamica()
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)
  const modeloStore = useWorkspaceStore(s => s.modeloDinamico)

  const { modelo } = form
  const disabled = isChatBusy || isSolving

  useEffect(() => {
    if (modeloStore) form.reemplazar(modeloStore)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeloStore])

  return (
    <div className="space-y-4">
      {ultimaActualizacionIA && !isChatBusy && (
        <ShaderGlow target="banner" state="done">
          <BotMessageSquare className="h-3.5 w-3.5 shrink-0" />
          <span>
            {ultimaActualizacionIA === 'modelo' && 'Pivot completó el modelo sugerido ✓'}
            {ultimaActualizacionIA === 'validacion' && 'Pivot revisó el modelo ✓'}
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa las tablas ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — el modelo se actualizará automáticamente
        </ShaderGlow>
      )}

      {/* Submodelo + sentido */}
      <div className="flex flex-wrap gap-6 items-end">
        <div>
          <LabelSeccion>Submodelo</LabelSeccion>
          <select
            value={modelo.metodo}
            disabled={disabled}
            onChange={e => form.setMetodo(e.target.value as MetodoDinamico)}
            className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
            style={{ ...MONO, color: 'var(--ij-orange)' }}
          >
            <option value="ASIGNACION_RECURSOS">Asignación de recursos</option>
            <option value="MOCHILA">Mochila</option>
            <option value="RUTA_ETAPAS">Ruta por etapas</option>
            <option value="PLANIFICACION_PRODUCCION">Planificación de producción</option>
            <option value="REEMPLAZO_EQUIPOS">Reemplazo de equipos</option>
          </select>
        </div>
        {sentidoConfigurable(modelo.metodo) && (
          <div>
            <LabelSeccion>Sentido</LabelSeccion>
            <select
              value={modelo.sentido}
              disabled={disabled}
              onChange={e => form.setCampo('sentido', e.target.value as SentidoOptimizacion)}
              className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed"
              style={{ ...MONO, color: 'var(--ij-text-secondary)' }}
            >
              <option value="MAXIMIZAR">Maximizar</option>
              <option value="MINIMIZAR">Minimizar</option>
            </select>
          </div>
        )}
      </div>
      <p className="text-xs" style={{ color: 'var(--ij-text-secondary)', marginTop: '-8px' }}>
        {METODO_HINT[modelo.metodo]}
      </p>

      {modelo.metodo === 'ASIGNACION_RECURSOS' && <FormAsignacion form={form} disabled={disabled} />}
      {modelo.metodo === 'MOCHILA' && <FormMochila form={form} disabled={disabled} />}
      {modelo.metodo === 'RUTA_ETAPAS' && <FormRuta form={form} disabled={disabled} />}
      {modelo.metodo === 'PLANIFICACION_PRODUCCION' && <FormPlanificacion form={form} disabled={disabled} />}
      {modelo.metodo === 'REEMPLAZO_EQUIPOS' && <FormReemplazo form={form} disabled={disabled} />}

      {error && <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>}

      <div className="flex gap-2 justify-end">
        <Button onClick={() => resolver(modelo.metodo, aModeloDinamico(modelo))} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver →
        </Button>
      </div>
    </div>
  )
}

type Form = ReturnType<typeof useDinamicaForm>

// ─── helpers compartidos ─────────────────────────────────────────────────────────

function LabelSeccion({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--ij-text-secondary)' }}>
      {children}
    </p>
  )
}

function NumInput({ value, onChange, disabled, color = 'var(--ij-cyan)', width = 'w-16', step = 'any' }: {
  value: number; onChange: (v: number) => void; disabled: boolean; color?: string; width?: string; step?: string
}) {
  return (
    <input
      type="number"
      step={step}
      value={value === 0 ? '' : value}
      placeholder="0"
      disabled={disabled}
      onChange={e => onChange(parseFloat(e.target.value) || 0)}
      className={`${width} h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed`}
      style={{ ...MONO, background: 'var(--ij-bg-secondary)', color, border: '1px solid var(--ij-border)', outline: 'none' }}
    />
  )
}

function TextInput({ value, onChange, disabled, color = 'var(--ij-purple)', width = 'w-20' }: {
  value: string; onChange: (v: string) => void; disabled: boolean; color?: string; width?: string
}) {
  return (
    <input
      type="text"
      value={value}
      disabled={disabled}
      onChange={e => onChange(e.target.value)}
      className={`${width} h-8 text-center rounded-[4px] disabled:opacity-50 disabled:cursor-not-allowed`}
      style={{ ...MONO, background: 'var(--ij-bg-secondary)', color, border: '1px solid var(--ij-border)', outline: 'none' }}
    />
  )
}

// ─── Asignación de recursos ──────────────────────────────────────────────────────

function FormAsignacion({ form, disabled }: { form: Form; disabled: boolean }) {
  const { modelo } = form
  return (
    <div className="space-y-4">
      <div className="flex items-end gap-3">
        <div>
          <LabelSeccion>Recurso total</LabelSeccion>
          <NumInput value={modelo.recursoTotal} onChange={form.setRecursoTotal} disabled={disabled} color="var(--ij-orange)" step="1" />
        </div>
        <p className="text-xs pb-2" style={{ color: 'var(--ij-text-secondary)' }}>
          Cada actividad necesita un retorno por cada nivel 0..{modelo.recursoTotal}
        </p>
      </div>

      <div>
        <LabelSeccion>Actividades × retorno por nivel asignado</LabelSeccion>
        <div className="overflow-x-auto">
          <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
            <thead>
              <tr style={{ ...MONO, fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
                <th className="font-semibold">Actividad</th>
                {Array.from({ length: modelo.recursoTotal + 1 }, (_, k) => (
                  <th key={k} className="font-semibold">x={k}</th>
                ))}
                <th />
              </tr>
            </thead>
            <tbody>
              {modelo.actividades.map((a, i) => (
                <tr key={i}>
                  <td><TextInput value={a.nombre} onChange={v => form.setNombreActividad(i, v)} disabled={disabled} width="w-24" /></td>
                  {a.retornos.map((r, k) => (
                    <td key={k}><NumInput value={r} onChange={v => form.setRetorno(i, k, v)} disabled={disabled} /></td>
                  ))}
                  <td>
                    <IconButton disabled={disabled || modelo.actividades.length <= 1} onClick={() => form.quitarActividad(i)} title="Quitar actividad">
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarActividad} className="mt-1">
          <Plus className="h-3.5 w-3.5 mr-1" /> Actividad
        </Button>
      </div>
    </div>
  )
}

// ─── Mochila ─────────────────────────────────────────────────────────────────────

function FormMochila({ form, disabled }: { form: Form; disabled: boolean }) {
  const { modelo } = form
  return (
    <div className="space-y-4">
      <div>
        <LabelSeccion>Capacidad</LabelSeccion>
        <NumInput value={modelo.capacidad} onChange={v => form.setCampo('capacidad', v)} disabled={disabled} color="var(--ij-orange)" step="1" />
      </div>

      <div>
        <LabelSeccion>Artículos</LabelSeccion>
        <div className="overflow-x-auto">
          <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
            <thead>
              <tr style={{ ...MONO, fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
                <th className="font-semibold">Nombre</th>
                <th className="font-semibold">Peso</th>
                <th className="font-semibold">Valor</th>
                <th className="font-semibold">Varias unidades</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {modelo.articulos.map((a, i) => (
                <tr key={i}>
                  <td><TextInput value={a.nombre} onChange={v => form.setArticulo(i, { nombre: v })} disabled={disabled} /></td>
                  <td><NumInput value={a.peso} onChange={v => form.setArticulo(i, { peso: v })} disabled={disabled} step="1" /></td>
                  <td><NumInput value={a.valor} onChange={v => form.setArticulo(i, { valor: v })} disabled={disabled} /></td>
                  <td className="text-center">
                    <label className="inline-flex items-center gap-1.5" style={{ ...MONO, fontSize: '12px', color: 'var(--ij-text-secondary)' }}>
                      <input type="checkbox" disabled={disabled} checked={a.unidadesMaximas != null} onChange={() => form.toggleUnidades(i)} />
                      {a.unidadesMaximas != null && (
                        <NumInput value={a.unidadesMaximas} onChange={v => form.setArticulo(i, { unidadesMaximas: Math.max(1, Math.round(v)) })} disabled={disabled} width="w-14" step="1" />
                      )}
                    </label>
                  </td>
                  <td>
                    <IconButton disabled={disabled || modelo.articulos.length <= 1} onClick={() => form.quitarArticulo(i)} title="Quitar artículo">
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarArticulo} className="mt-1">
          <Plus className="h-3.5 w-3.5 mr-1" /> Artículo
        </Button>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          Sin marcar "varias unidades" el artículo es 0/1 (llevar o no). El peso debe ser entero positivo.
        </p>
      </div>
    </div>
  )
}

// ─── Ruta por etapas ─────────────────────────────────────────────────────────────

function FormRuta({ form, disabled }: { form: Form; disabled: boolean }) {
  const { modelo } = form
  const nodos = nodosDeRuta(modelo)

  const nodoSelect = (value: string, onChange: (v: string) => void) => (
    <select
      value={value}
      disabled={disabled}
      onChange={e => onChange(e.target.value)}
      className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed"
      style={{ ...MONO, color: 'var(--ij-purple)' }}
    >
      {nodos.map(n => <option key={n} value={n}>{n}</option>)}
    </select>
  )

  return (
    <div className="space-y-4">
      <div>
        <LabelSeccion>Etapas y nodos</LabelSeccion>
        <div className="space-y-2">
          {modelo.etapasRuta.map((etapa, ei) => (
            <div key={ei} className="flex items-center gap-2 flex-wrap">
              <span className="text-xs font-semibold" style={{ color: 'var(--ij-teal)', width: '64px' }}>Etapa {etapa.etapa}</span>
              {etapa.nodos.map((nodo, ni) => (
                <span key={ni} className="flex items-center gap-1">
                  <TextInput value={nodo} onChange={v => form.setNodo(ei, ni, v)} disabled={disabled} width="w-16" />
                  {etapa.nodos.length > 1 && (
                    <IconButton disabled={disabled} onClick={() => form.quitarNodoEtapa(ei, ni)} title="Quitar nodo">
                      <X className="h-3.5 w-3.5" />
                    </IconButton>
                  )}
                </span>
              ))}
              <Button variant="ghost" size="sm" disabled={disabled} onClick={() => form.agregarNodoEtapa(ei)}>
                <Plus className="h-3.5 w-3.5 mr-1" /> Nodo
              </Button>
            </div>
          ))}
        </div>
        <div className="mt-2 flex gap-2">
          <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarEtapa}>
            <Plus className="h-3.5 w-3.5 mr-1" /> Etapa
          </Button>
          {modelo.etapasRuta.length > 2 && (
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarEtapa}>
              <Trash2 className="h-3.5 w-3.5 mr-1" /> Etapa
            </Button>
          )}
        </div>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          La etapa 1 debe tener un solo nodo (el origen); los nombres de nodo deben ser únicos.
        </p>
      </div>

      <div>
        <LabelSeccion>Arcos · origen → destino : costo</LabelSeccion>
        <div className="overflow-x-auto">
          <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
            <tbody>
              {modelo.arcos.map((a, i) => (
                <tr key={i}>
                  <td>{nodoSelect(a.origen, v => form.setArco(i, { origen: v }))}</td>
                  <td style={{ color: 'var(--ij-text-secondary)' }}>→</td>
                  <td>{nodoSelect(a.destino, v => form.setArco(i, { destino: v }))}</td>
                  <td><NumInput value={a.costo} onChange={v => form.setArco(i, { costo: v })} disabled={disabled} /></td>
                  <td>
                    <IconButton disabled={disabled} onClick={() => form.quitarArco(i)} title="Quitar arco">
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarArco} className="mt-1">
          <Plus className="h-3.5 w-3.5 mr-1" /> Arco
        </Button>
      </div>
    </div>
  )
}

// ─── Planificación de producción ─────────────────────────────────────────────────

function FormPlanificacion({ form, disabled }: { form: Form; disabled: boolean }) {
  const { modelo } = form
  const [avanzado, setAvanzado] = useState(false)
  return (
    <div className="space-y-4">
      <div>
        <LabelSeccion>Demanda por periodo</LabelSeccion>
        <div className="flex items-center gap-2 flex-wrap">
          {modelo.demandas.map((d, i) => (
            <span key={i} className="flex flex-col items-center gap-1">
              <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>t{i + 1}</span>
              <NumInput value={d} onChange={v => form.setDemanda(i, v)} disabled={disabled} step="1" />
            </span>
          ))}
          <div className="flex gap-1 pt-4">
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarPeriodo}>
              <Plus className="h-3.5 w-3.5 mr-1" /> Periodo
            </Button>
            {modelo.demandas.length > 1 && (
              <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarPeriodo}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            )}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap gap-4">
        <Campo label="Costo de preparación (K)"><NumInput value={modelo.costoPreparacion} onChange={v => form.setCampo('costoPreparacion', v)} disabled={disabled} width="w-24" /></Campo>
        <Campo label="Costo unitario producción (c)"><NumInput value={modelo.costoUnitarioProduccion} onChange={v => form.setCampo('costoUnitarioProduccion', v)} disabled={disabled} width="w-24" /></Campo>
        <Campo label="Costo de mantener (h)"><NumInput value={modelo.costoMantener} onChange={v => form.setCampo('costoMantener', v)} disabled={disabled} width="w-24" /></Campo>
      </div>

      <div>
        <button
          onClick={() => setAvanzado(v => !v)}
          className="flex items-center gap-1.5 transition-colors duration-[120ms]"
          style={{ fontSize: '12px', color: 'var(--ij-text-secondary)', background: 'none', border: 'none', cursor: 'pointer' }}
        >
          <ChevronDown className={`h-3.5 w-3.5 transition-transform ${avanzado ? 'rotate-180' : ''}`} />
          Opciones avanzadas (capacidades e inventario)
        </button>
        {avanzado && (
          <div className="flex flex-wrap gap-4 mt-2">
            <Campo label="Capacidad producción (0 = sin límite)"><NumInput value={modelo.capacidadProduccion} onChange={v => form.setCampo('capacidadProduccion', v)} disabled={disabled} width="w-24" step="1" /></Campo>
            <Campo label="Capacidad almacén (0 = sin límite)"><NumInput value={modelo.capacidadAlmacen} onChange={v => form.setCampo('capacidadAlmacen', v)} disabled={disabled} width="w-24" step="1" /></Campo>
            <Campo label="Inventario inicial"><NumInput value={modelo.inventarioInicial} onChange={v => form.setCampo('inventarioInicial', v)} disabled={disabled} width="w-24" step="1" /></Campo>
            <Campo label="Inventario final"><NumInput value={modelo.inventarioFinal} onChange={v => form.setCampo('inventarioFinal', v)} disabled={disabled} width="w-24" step="1" /></Campo>
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Reemplazo de equipos ────────────────────────────────────────────────────────

function FormReemplazo({ form, disabled }: { form: Form; disabled: boolean }) {
  const { modelo } = form
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-4">
        <Campo label="Horizonte (años)"><NumInput value={modelo.horizonteAnios} onChange={v => form.setCampo('horizonteAnios', Math.max(1, Math.round(v)))} disabled={disabled} step="1" /></Campo>
        <Campo label="Edad máxima"><NumInput value={modelo.edadMaxima} onChange={form.setEdadMaxima} disabled={disabled} step="1" color="var(--ij-orange)" /></Campo>
        <Campo label="Edad inicial"><NumInput value={modelo.edadInicial} onChange={v => form.setCampo('edadInicial', Math.max(0, Math.round(v)))} disabled={disabled} step="1" /></Campo>
        <Campo label="Costo de compra"><NumInput value={modelo.costoCompra} onChange={v => form.setCampo('costoCompra', v)} disabled={disabled} width="w-24" /></Campo>
      </div>

      <div>
        <LabelSeccion>Datos por edad del equipo</LabelSeccion>
        <div className="overflow-x-auto">
          <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
            <thead>
              <tr style={{ ...MONO, fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
                <th className="font-semibold">Edad</th>
                <th className="font-semibold">Ingreso</th>
                <th className="font-semibold">Costo operación</th>
                <th className="font-semibold">Valor rescate</th>
              </tr>
            </thead>
            <tbody>
              {modelo.tablaEdades.map(e => (
                <tr key={e.edad}>
                  <td className="text-center font-semibold" style={{ ...MONO, color: 'var(--ij-purple)' }}>{e.edad}</td>
                  <td><NumInput value={e.ingreso} onChange={v => form.setEdadDato(e.edad, { ingreso: v })} disabled={disabled} /></td>
                  <td><NumInput value={e.costoOperacion} onChange={v => form.setEdadDato(e.edad, { costoOperacion: v })} disabled={disabled} /></td>
                  <td><NumInput value={e.valorRescate} onChange={v => form.setEdadDato(e.edad, { valorRescate: v })} disabled={disabled} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          La tabla cubre todas las edades 0..{modelo.edadMaxima} (se regenera al cambiar la edad máxima).
        </p>
      </div>
    </div>
  )
}

// ─── átomos ──────────────────────────────────────────────────────────────────────

function Campo({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>{label}</span>
      {children}
    </div>
  )
}

function IconButton({ onClick, disabled, title, children }: {
  onClick: () => void; disabled: boolean; title: string; children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className="disabled:opacity-30 disabled:cursor-not-allowed"
      style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ij-text-muted)', padding: 0 }}
    >
      {children}
    </button>
  )
}
