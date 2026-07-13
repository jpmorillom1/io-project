import { useEffect, type CSSProperties } from 'react'
import { useRedForm, aModeloRed } from '@/hooks/redes/useRedForm'
import { useResolverRed } from '@/hooks/redes/useResolverRed'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { Button } from '@/components/ui/button'
import { Trash2, Plus, Loader2, BotMessageSquare, X } from 'lucide-react'
import { ShaderGlow } from '@/components/ui/ShaderGlow'
import type { MetodoRed } from '@/types/io'

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace", fontSize: '13px' }

const METODO_HINT: Record<MetodoRed, string> = {
  DIJKSTRA: 'Ruta más corta desde la fuente (pesos ≥ 0); el sumidero es opcional',
  KRUSKAL: 'Árbol de expansión mínima; el grafo se trata como no dirigido',
  EDMONDS_KARP: 'Flujo máximo fuente→sumidero por caminos de aumento (BFS)',
  FLUJO_COSTO_MINIMO: 'Flujo máximo de costo mínimo fuente→sumidero',
  ASIGNACION: 'Asignación óptima agentes→tareas (se balancea con "Ficticio" si n≠m)',
}

/**
 * Editor del modelo de redes. Espeja TransporteModelEditor: banners de Pivot,
 * labels en versalitas, inputs monoespaciados. Para métodos de grafo muestra
 * nodos + tabla de aristas (solo las columnas del método) + fuente/sumidero;
 * para ASIGNACION, la matriz agentes×tareas.
 */
export function RedModelEditor() {
  const form = useRedForm()
  const { resolver, isSolving, error } = useResolverRed()
  const isChatBusy = useWorkspaceStore(s => s.isChatBusy)
  const ultimaActualizacionIA = useWorkspaceStore(s => s.ultimaActualizacionIA)
  const modeloStore = useWorkspaceStore(s => s.modeloRed)
  const setModeloGrafico = useWorkspaceStore(s => s.setModeloRedGrafico)
  const setStatus = useWorkspaceStore(s => s.setStatus)

  const { modelo } = form
  const disabled = isChatBusy || isSolving

  const esAsignacion = modelo.metodo === 'ASIGNACION'
  const esKruskal = modelo.metodo === 'KRUSKAL'
  const conPeso = modelo.metodo === 'DIJKSTRA' || modelo.metodo === 'KRUSKAL'
  const conCapacidad = modelo.metodo === 'EDMONDS_KARP' || modelo.metodo === 'FLUJO_COSTO_MINIMO'
  const conCosto = modelo.metodo === 'FLUJO_COSTO_MINIMO'

  // Cuando el chat propone/aprueba un problema de redes, refleja el modelo aquí.
  useEffect(() => {
    if (modeloStore) form.reemplazar(modeloStore)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeloStore])

  function numInput(value: number | null | undefined, onChange: (v: number) => void, color = 'var(--ij-cyan)') {
    const v = value ?? 0
    return (
      <input
        type="number"
        value={v === 0 ? '' : v}
        placeholder="0"
        disabled={disabled}
        onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-16 h-8 text-center rounded-[4px] tabular-nums disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color, border: '1px solid var(--ij-border)', outline: 'none' }}
      />
    )
  }

  function nameInput(value: string, onChange: (v: string) => void, color: string, width = 'w-20') {
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

  function nodoSelect(value: string, onChange: (v: string) => void, incluirVacio = false) {
    return (
      <select
        value={value}
        disabled={disabled}
        onChange={e => onChange(e.target.value)}
        className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ ...MONO, background: 'var(--ij-bg-secondary)', color: 'var(--ij-purple)', border: '1px solid var(--ij-border)', outline: 'none' }}
      >
        {incluirVacio && <option value="">—</option>}
        {modelo.nodos.map(n => (
          <option key={n} value={n}>{n}</option>
        ))}
      </select>
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
            {ultimaActualizacionIA === 'resultado' && 'Pivot resolvió el problema — revisa el grafo ✓'}
          </span>
        </ShaderGlow>
      )}

      {isChatBusy && (
        <ShaderGlow target="banner" state="processing">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Pivot está respondiendo — el modelo se actualizará automáticamente
        </ShaderGlow>
      )}

      {/* Método */}
      <div>
        {labelSeccion('Método')}
        <select
          value={modelo.metodo}
          disabled={disabled}
          onChange={e => form.setMetodo(e.target.value as MetodoRed)}
          className="h-8 rounded-[4px] px-2 disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
          style={{ ...MONO, color: 'var(--ij-orange)' }}
        >
          <option value="DIJKSTRA">Dijkstra (ruta más corta)</option>
          <option value="KRUSKAL">Kruskal (árbol mínimo)</option>
          <option value="EDMONDS_KARP">Edmonds-Karp (flujo máximo)</option>
          <option value="FLUJO_COSTO_MINIMO">Flujo de costo mínimo</option>
          <option value="ASIGNACION">Asignación</option>
        </select>
        <p className="text-xs mt-1.5" style={{ color: 'var(--ij-text-secondary)' }}>
          {METODO_HINT[modelo.metodo]}
        </p>
      </div>

      {esAsignacion ? (
        /* ── Matriz agentes × tareas ─────────────────────────────────────── */
        <div>
          {labelSeccion('Matriz de costos · Agentes × Tareas')}
          <div className="overflow-x-auto">
            <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
              <thead>
                <tr>
                  <th />
                  {modelo.tareas.map((t, j) => (
                    <th key={j}>{nameInput(t, v => form.setNombreTarea(j, v), 'var(--ij-teal)')}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {modelo.agentes.map((a, i) => (
                  <tr key={i}>
                    <td>{nameInput(a, v => form.setNombreAgente(i, v), 'var(--ij-purple)')}</td>
                    {modelo.tareas.map((_, j) => (
                      <td key={j}>{numInput(modelo.matrizCostos[i][j], v => form.setCosto(i, j, v))}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-2 flex gap-2 flex-wrap">
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarAgente}>
              <Plus className="h-3.5 w-3.5 mr-1" />
              Agente
            </Button>
            {modelo.agentes.length > 1 && (
              <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarAgente}>
                <Trash2 className="h-3.5 w-3.5 mr-1" />
                Agente
              </Button>
            )}
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarTarea}>
              <Plus className="h-3.5 w-3.5 mr-1" />
              Tarea
            </Button>
            {modelo.tareas.length > 1 && (
              <Button variant="ghost" size="sm" disabled={disabled} onClick={form.quitarTarea}>
                <Trash2 className="h-3.5 w-3.5 mr-1" />
                Tarea
              </Button>
            )}
          </div>

          {modelo.agentes.length !== modelo.tareas.length && (
            <p className="text-xs mt-2" style={{ color: 'var(--ij-amber)' }}>
              {modelo.agentes.length} agentes ≠ {modelo.tareas.length} tareas — se balanceará con un "Ficticio" de costo 0
            </p>
          )}
        </div>
      ) : (
        /* ── Grafo: nodos + aristas + fuente/sumidero ────────────────────── */
        <>
          <div>
            {labelSeccion('Nodos')}
            <div className="flex gap-2 flex-wrap items-center">
              {modelo.nodos.map((n, i) => (
                <span key={i} className="flex items-center gap-1">
                  {nameInput(n, v => form.renombrarNodo(i, v), 'var(--ij-purple)', 'w-16')}
                  {modelo.nodos.length > 2 && (
                    <button
                      disabled={disabled}
                      onClick={() => form.quitarNodo(i)}
                      title={`Quitar ${n} (y sus aristas)`}
                      className="disabled:opacity-50"
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ij-text-muted)', padding: 0 }}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                </span>
              ))}
              <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarNodo}>
                <Plus className="h-3.5 w-3.5 mr-1" />
                Nodo
              </Button>
            </div>
          </div>

          <div>
            {labelSeccion(
              conCosto ? 'Aristas · Capacidad · Costo' : conCapacidad ? 'Aristas · Capacidad' : 'Aristas · Peso'
            )}
            <div className="overflow-x-auto">
              <table style={{ borderCollapse: 'separate', borderSpacing: '6px' }}>
                <thead>
                  <tr style={{ ...MONO, fontSize: '11px', color: 'var(--ij-text-secondary)' }}>
                    <th className="font-semibold">Origen</th>
                    <th className="font-semibold">Destino</th>
                    {conPeso && <th className="font-semibold">Peso</th>}
                    {conCapacidad && <th className="font-semibold">Capacidad</th>}
                    {conCosto && <th className="font-semibold">Costo</th>}
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {modelo.aristas.map((a, i) => (
                    <tr key={i}>
                      <td>{nodoSelect(a.origen, v => form.setArista(i, { origen: v }))}</td>
                      <td>{nodoSelect(a.destino, v => form.setArista(i, { destino: v }))}</td>
                      {conPeso && <td>{numInput(a.peso, v => form.setArista(i, { peso: v }))}</td>}
                      {conCapacidad && <td>{numInput(a.capacidad, v => form.setArista(i, { capacidad: v }), 'var(--ij-orange)')}</td>}
                      {conCosto && <td>{numInput(a.costo, v => form.setArista(i, { costo: v }))}</td>}
                      <td>
                        <button
                          disabled={disabled}
                          onClick={() => form.quitarArista(i)}
                          title="Quitar arista"
                          className="disabled:opacity-50"
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
            <Button variant="ghost" size="sm" disabled={disabled} onClick={form.agregarArista} className="mt-1">
              <Plus className="h-3.5 w-3.5 mr-1" />
              Arista
            </Button>
          </div>

          {!esKruskal && (
            <div className="flex gap-6 flex-wrap items-end">
              <div>
                {labelSeccion(modelo.metodo === 'DIJKSTRA' ? 'Origen (fuente)' : 'Fuente')}
                {nodoSelect(modelo.fuente, form.setFuente)}
              </div>
              <div>
                {labelSeccion(modelo.metodo === 'DIJKSTRA' ? 'Destino (opcional)' : 'Sumidero')}
                {nodoSelect(modelo.sumidero, form.setSumidero, modelo.metodo === 'DIJKSTRA')}
              </div>
              {modelo.metodo === 'DIJKSTRA' && (
                <label className="flex items-center gap-2 text-xs pb-2" style={{ color: 'var(--ij-text-secondary)' }}>
                  <input
                    type="checkbox"
                    checked={modelo.dirigido}
                    disabled={disabled}
                    onChange={e => form.setDirigido(e.target.checked)}
                  />
                  Grafo dirigido
                </label>
              )}
            </div>
          )}
        </>
      )}

      {error && <p className="text-xs" style={{ color: 'var(--ij-red)' }}>{error}</p>}

      <div className="flex gap-2 justify-end">
        <Button
          variant="outline"
          onClick={() => { setModeloGrafico(aModeloRed(modelo)); setStatus('EDITING') }}
          disabled={disabled}
        >
          Graficar
        </Button>
        <Button onClick={() => resolver(aModeloRed(modelo))} disabled={disabled}>
          {isSolving && <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />}
          Resolver →
        </Button>
      </div>
    </div>
  )
}
