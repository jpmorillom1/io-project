import { formatNum } from '@/lib/utils'
import type { SolveResultRed, MetodoRed } from '@/types/io'

interface Props {
  resultado: SolveResultRed
}

const MONO = { fontFamily: "'JetBrains Mono', monospace" } as const

/**
 * Banner de resultado de redes. Espeja TransporteResultBanner: caja verde con
 * el valor objetivo y el detalle según el método; caja roja para INFACTIBLE
 * (desconexo/inalcanzable/sin camino — NO es un error, el último paso lo explica).
 */
export function RedResultBanner({ resultado }: Props) {
  const metodo: MetodoRed | undefined = resultado.steps[0]?.datos.metodo

  if ((resultado.status === 'OPTIMO' || resultado.status === 'MULTIPLE_OPTIMO') && resultado.solution) {
    const sol = resultado.solution
    return (
      <div
        className="rounded-[4px] p-4 space-y-3"
        style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
      >
        <div>
          <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>
            {resultado.status === 'OPTIMO' ? 'Solución óptima' : 'Soluciones óptimas múltiples'}
          </p>
          <p className="mt-1 text-lg" style={{ ...MONO, color: 'var(--ij-green)' }}>
            <span style={{ color: 'var(--ij-text-secondary)' }}>{tituloValor(metodo)} = </span>
            {formatNum(sol.valorObjetivo ?? 0)}
          </p>
          {metodo === 'FLUJO_COSTO_MINIMO' && sol.flujoTotal != null && (
            <p className="text-sm" style={{ ...MONO, color: 'var(--ij-text-primary)' }}>
              <span style={{ color: 'var(--ij-text-secondary)' }}>Flujo enviado = </span>
              {formatNum(sol.flujoTotal)}
            </p>
          )}
        </div>

        {/* Dijkstra: ruta óptima o distancias a todos los nodos */}
        {metodo === 'DIJKSTRA' && (
          sol.rutaOptima && sol.rutaOptima.length > 0 ? (
            <Detalle titulo="Ruta óptima">
              <span className="text-sm" style={MONO}>
                {sol.rutaOptima.map((n, i) => (
                  <span key={i}>
                    {i > 0 && <span style={{ color: 'var(--ij-text-secondary)' }}>{' → '}</span>}
                    <span style={{ color: 'var(--ij-purple)' }}>{n}</span>
                  </span>
                ))}
              </span>
            </Detalle>
          ) : sol.distancias ? (
            <Detalle titulo="Distancias mínimas desde la fuente">
              <div className="flex gap-4 flex-wrap">
                {Object.entries(sol.distancias).map(([nodo, d]) => (
                  <span key={nodo} className="text-sm" style={MONO}>
                    <span style={{ color: 'var(--ij-purple)' }}>{nodo}</span>
                    <span style={{ color: 'var(--ij-text-secondary)' }}>{' = '}</span>
                    <span style={{ color: 'var(--ij-text-primary)' }}>{formatNum(d)}</span>
                  </span>
                ))}
              </div>
            </Detalle>
          ) : null
        )}

        {/* Kruskal: aristas del árbol */}
        {metodo === 'KRUSKAL' && sol.aristasSolucion && (
          <Detalle titulo="Aristas del árbol de expansión mínima">
            <div className="flex flex-col gap-0.5">
              {sol.aristasSolucion.map((a, i) => (
                <span key={i} className="text-sm" style={MONO}>
                  <span style={{ color: 'var(--ij-purple)' }}>{a.origen}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' — '}</span>
                  <span style={{ color: 'var(--ij-purple)' }}>{a.destino}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' : '}</span>
                  <span style={{ color: 'var(--ij-green)' }}>{formatNum(a.peso ?? 0)}</span>
                </span>
              ))}
            </div>
          </Detalle>
        )}

        {/* EK / MCF: flujo por arco */}
        {(metodo === 'EDMONDS_KARP' || metodo === 'FLUJO_COSTO_MINIMO') && sol.flujoPorArco && (
          <Detalle titulo="Flujo por arco">
            <div className="flex gap-4 flex-wrap">
              {Object.entries(sol.flujoPorArco).map(([arco, f]) => (
                <span key={arco} className="text-sm" style={MONO}>
                  <span style={{ color: 'var(--ij-purple)' }}>{arco.replace('->', ' → ')}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' : '}</span>
                  <span style={{ color: 'var(--ij-green)' }}>{formatNum(f)}</span>
                </span>
              ))}
            </div>
          </Detalle>
        )}

        {/* Asignación: pares agente → tarea */}
        {metodo === 'ASIGNACION' && sol.asignacion && (
          <Detalle titulo="Asignación óptima">
            <div className="flex flex-col gap-0.5">
              {Object.entries(sol.asignacion).map(([agente, tarea]) => (
                <span key={agente} className="text-sm" style={MONO}>
                  <span style={{ color: 'var(--ij-purple)' }}>{agente}</span>
                  <span style={{ color: 'var(--ij-text-secondary)' }}>{' → '}</span>
                  <span style={{ color: 'var(--ij-teal)' }}>{tarea}</span>
                </span>
              ))}
            </div>
          </Detalle>
        )}
      </div>
    )
  }

  const msgs: Record<string, string> = {
    INFACTIBLE: mensajeInfactible(metodo),
    NO_ACOTADO: 'El problema no está acotado.',
    ERROR: 'El solver encontró un error interno al procesar el modelo.',
  }
  return (
    <div className="rounded-[4px] p-4" style={{ background: 'rgba(255,82,99,0.08)', borderLeft: '2px solid var(--ij-red)' }}>
      <p className="font-semibold text-sm" style={{ color: 'var(--ij-red)' }}>{resultado.status}</p>
      <p className="mt-1 text-sm" style={{ color: 'var(--ij-red)', opacity: 0.85 }}>
        {msgs[resultado.status] ?? 'Estado desconocido'}
      </p>
    </div>
  )
}

function Detalle({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <div>
      <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
        {titulo}
      </p>
      {children}
    </div>
  )
}

function tituloValor(metodo: MetodoRed | undefined): string {
  switch (metodo) {
    case 'DIJKSTRA': return 'Distancia mínima'
    case 'KRUSKAL': return 'Peso del árbol'
    case 'EDMONDS_KARP': return 'Flujo máximo'
    case 'FLUJO_COSTO_MINIMO': return 'Costo mínimo'
    case 'ASIGNACION': return 'Costo total'
    default: return 'Valor objetivo'
  }
}

function mensajeInfactible(metodo: MetodoRed | undefined): string {
  switch (metodo) {
    case 'DIJKSTRA': return 'El destino es inalcanzable desde la fuente: no existe ruta.'
    case 'KRUSKAL': return 'El grafo es desconexo: no existe un árbol que conecte todos los nodos.'
    case 'EDMONDS_KARP':
    case 'FLUJO_COSTO_MINIMO': return 'No existe ningún camino de la fuente al sumidero: el flujo máximo es 0.'
    case 'ASIGNACION': return 'No existe una asignación factible de agentes a tareas.'
    default: return 'El problema no tiene solución factible. El último paso explica el motivo.'
  }
}
