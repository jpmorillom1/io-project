import { type CSSProperties } from 'react'
import { RutaGrafo } from './RutaGrafo'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import { formatNum } from '@/lib/utils'
import type {
  SolveResultDinamica, TablaEtapa, DecisionOptima, MetodoDinamico,
} from '@/types/io'

interface Props {
  resultado: SolveResultDinamica
}

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace" }

interface Formulacion {
  definicionEtapas?: string
  definicionEstados?: string
  definicionDecisiones?: string
  funcionRecurrencia?: string
  principioOptimalidad?: string
}

/**
 * Visor de Programación Dinámica. Muestra los 7 elementos del modelo: formulación
 * (etapas/estados/decisiones/recurrencia/principio), una tabla por etapa (en el
 * orden hacia atrás en que llegan) y la política óptima recuperada hacia adelante.
 * Con INFACTIBLE, `solution` es null pero los pasos traen las tablas calculadas
 * hasta el fallo: se muestran igual como explicación.
 */
export function DinamicaResultViewer({ resultado }: Props) {
  const modeloDinamico = useWorkspaceStore(s => s.modeloDinamico)
  const metodo: MetodoDinamico | undefined = resultado.steps[0]?.datos.metodo
  const sol = resultado.solution

  // Formulación: de la solución si existe, si no del paso de formulación.
  const pasoFormulacion = resultado.steps.find(s => s.datos.etapas != null)
  const formulacion: Formulacion = sol
    ? {
        definicionEtapas: sol.definicionEtapas,
        definicionEstados: sol.definicionEstados,
        definicionDecisiones: sol.definicionDecisiones,
        funcionRecurrencia: sol.funcionRecurrencia,
        principioOptimalidad: sol.principioOptimalidad,
      }
    : {
        definicionEtapas: pasoFormulacion?.datos.etapas,
        definicionEstados: pasoFormulacion?.datos.estados,
        definicionDecisiones: pasoFormulacion?.datos.decisiones,
        funcionRecurrencia: pasoFormulacion?.datos.recurrencia,
        principioOptimalidad: pasoFormulacion?.datos.principioOptimalidad,
      }

  // Tablas: de la solución si existe, si no de los pasos de etapa.
  const tablas: TablaEtapa[] = sol
    ? sol.tablas
    : resultado.steps.map(s => s.datos.tabla).filter((t): t is TablaEtapa => t != null)

  const infactible = resultado.status === 'INFACTIBLE' || !sol
  const razonInfactible = resultado.steps[resultado.steps.length - 1]?.descripcion

  const esRuta = metodo === 'RUTA_ETAPAS'
  const modeloRuta = esRuta && modeloDinamico?.metodo === 'RUTA_ETAPAS' ? modeloDinamico : null

  return (
    <div className="space-y-5">
      {/* Formulación — los 7 elementos del modelo */}
      <FormulacionCard f={formulacion} />

      {/* Grafo de la red por etapas (solo ruta-etapas) */}
      {modeloRuta && modeloRuta.etapasRuta && modeloRuta.arcos && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle>Red por etapas</CardTitle>
          </CardHeader>
          <CardContent className="pt-2">
            <RutaGrafo
              etapas={modeloRuta.etapasRuta}
              arcos={modeloRuta.arcos}
              rutaOptima={sol?.rutaOptima ?? null}
            />
          </CardContent>
        </Card>
      )}

      {/* Tablas de solución — una por etapa (orden hacia atrás) */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle>Tablas de solución (recursión hacia atrás)</CardTitle>
          <p className="text-xs" style={{ color: 'var(--ij-text-secondary)' }}>
            Cada tabla es una etapa; se resuelven de la última a la primera. En cada estado,
            <span style={{ color: 'var(--ij-text-primary)' }}> valor total = contribución + valor futuro</span>.
          </p>
        </CardHeader>
        <CardContent className="pt-2 space-y-5">
          {tablas.map((t, i) => <TablaEtapaView key={i} tabla={t} />)}
        </CardContent>
      </Card>

      {/* Política óptima o motivo de infactibilidad */}
      {infactible ? (
        <div className="rounded-[4px] p-4" style={{ background: 'rgba(255,82,99,0.08)', borderLeft: '2px solid var(--ij-red)' }}>
          <p className="font-semibold text-sm" style={{ color: 'var(--ij-red)' }}>INFACTIBLE</p>
          <p className="mt-1 text-sm" style={{ color: 'var(--ij-red)', opacity: 0.85 }}>
            {razonInfactible ?? 'El modelo no tiene solución factible. Las tablas de arriba muestran el cálculo hasta el punto del fallo.'}
          </p>
        </div>
      ) : (
        sol && <PoliticaOptima politica={sol.politicaOptima} rutaOptima={sol.rutaOptima} valorOptimo={sol.valorOptimo} interpretacion={sol.interpretacionPolitica} />
      )}
    </div>
  )
}

// ─── Formulación ────────────────────────────────────────────────────────────────

function FormulacionCard({ f }: { f: Formulacion }) {
  const defs: Array<[string, string | undefined]> = [
    ['Etapas', f.definicionEtapas],
    ['Estados', f.definicionEstados],
    ['Decisiones', f.definicionDecisiones],
  ]
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle>① Formulación del modelo</CardTitle>
      </CardHeader>
      <CardContent className="pt-2 space-y-3">
        {defs.map(([label, texto]) => texto && (
          <div key={label}>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600 }}>{label}</p>
            <p className="text-sm" style={{ color: 'var(--ij-text-primary)', lineHeight: 1.5 }}>{texto}</p>
          </div>
        ))}
        {f.funcionRecurrencia && (
          <div>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600 }}>Función de recurrencia</p>
            <div
              className="mt-1 rounded-[4px] px-3 py-2 overflow-x-auto"
              style={{ background: 'var(--ij-bg-secondary)', border: '1px solid var(--ij-border)', ...MONO, fontSize: '12px', color: 'var(--ij-purple)' }}
            >
              {f.funcionRecurrencia}
            </div>
          </div>
        )}
        {f.principioOptimalidad && (
          <div>
            <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600 }}>Principio de optimalidad</p>
            <p className="text-xs mt-0.5" style={{ color: 'var(--ij-text-secondary)', lineHeight: 1.55 }}>{f.principioOptimalidad}</p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ─── Tabla de una etapa ─────────────────────────────────────────────────────────

const celdaBase: CSSProperties = { border: '1px solid var(--ij-border)', padding: '3px 10px', textAlign: 'center', whiteSpace: 'nowrap' }
const cabecera: CSSProperties = { ...celdaBase, fontWeight: 600, color: 'var(--ij-text-secondary)' }

function TablaEtapaView({ tabla }: { tabla: TablaEtapa }) {
  return (
    <div>
      <div className="flex items-baseline gap-3 flex-wrap mb-1.5">
        <p className="text-sm font-semibold" style={{ color: 'var(--ij-teal)' }}>{tabla.nombreEtapa}</p>
        <span className="text-xs" style={{ ...MONO, color: 'var(--ij-text-secondary)' }}>{tabla.recurrencia}</span>
      </div>
      <div className="overflow-x-auto">
        <table style={{ ...MONO, fontSize: '12px', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={cabecera}>Estado</th>
              <th style={cabecera}>Decisión</th>
              <th style={cabecera}>Contribución</th>
              <th style={cabecera}>Valor futuro</th>
              <th style={cabecera}>Total</th>
              <th style={cabecera}>f(estado)</th>
            </tr>
          </thead>
          <tbody>
            {tabla.filas.map((fila, fi) => (
              fila.evaluaciones.map((ev, ei) => {
                const color = ev.optima ? 'var(--ij-green)' : 'var(--ij-text-primary)'
                return (
                  <tr key={`${fi}-${ei}`} style={{ background: ev.optima ? 'rgba(106,171,116,0.1)' : undefined }}>
                    {ei === 0 && (
                      <td style={{ ...celdaBase, color: 'var(--ij-purple)', fontWeight: 600 }} rowSpan={fila.evaluaciones.length}>
                        {fila.estado}
                      </td>
                    )}
                    <td style={{ ...celdaBase, color }}>{ev.decision}{ev.optima ? ' ★' : ''}</td>
                    <td style={{ ...celdaBase, color }}>{formatNum(ev.contribucion)}</td>
                    <td style={{ ...celdaBase, color }}>{formatNum(ev.valorFuturo)}</td>
                    <td style={{ ...celdaBase, color, fontWeight: ev.optima ? 700 : 400 }}>{formatNum(ev.valorTotal)}</td>
                    {ei === 0 && (
                      <td style={{ ...celdaBase, color: 'var(--ij-green)', fontWeight: 600 }} rowSpan={fila.evaluaciones.length}>
                        {formatNum(fila.valorOptimo)}
                      </td>
                    )}
                  </tr>
                )
              })
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ─── Política óptima ─────────────────────────────────────────────────────────────

function PoliticaOptima({
  politica, rutaOptima, valorOptimo, interpretacion,
}: {
  politica: DecisionOptima[]
  rutaOptima: string[] | null
  valorOptimo: number
  interpretacion: string
}) {
  return (
    <div
      className="rounded-[4px] p-4 space-y-3"
      style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
    >
      <div>
        <p className="font-semibold text-sm" style={{ color: 'var(--ij-green)' }}>Política óptima</p>
        <p className="mt-1 text-lg" style={{ ...MONO, color: 'var(--ij-green)' }}>
          <span style={{ color: 'var(--ij-text-secondary)' }}>Z* = </span>{formatNum(valorOptimo)}
        </p>
      </div>

      {rutaOptima && rutaOptima.length > 0 && (
        <p className="text-sm" style={MONO}>
          {rutaOptima.map((n, i) => (
            <span key={i}>
              {i > 0 && <span style={{ color: 'var(--ij-text-secondary)' }}>{' → '}</span>}
              <span style={{ color: 'var(--ij-purple)' }}>{n}</span>
            </span>
          ))}
        </p>
      )}

      <div className="overflow-x-auto">
        <table style={{ ...MONO, fontSize: '12px', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={cabecera}>Etapa</th>
              <th style={cabecera}>Estado entrada</th>
              <th style={cabecera}>Decisión</th>
              <th style={cabecera}>Contribución</th>
              <th style={cabecera}>Estado salida</th>
            </tr>
          </thead>
          <tbody>
            {politica.map((d, i) => (
              <tr key={i}>
                <td style={{ ...celdaBase, color: 'var(--ij-teal)', fontWeight: 600 }}>{d.nombreEtapa}</td>
                <td style={{ ...celdaBase, color: 'var(--ij-text-primary)' }}>{d.estadoEntrada}</td>
                <td style={{ ...celdaBase, color: 'var(--ij-green)', fontWeight: 600 }}>{d.decision}</td>
                <td style={{ ...celdaBase, color: 'var(--ij-text-primary)' }}>{formatNum(d.contribucion)}</td>
                <td style={{ ...celdaBase, color: 'var(--ij-text-primary)' }}>{d.estadoSalida}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div>
        <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '2px' }}>
          Interpretación de la política
        </p>
        <p className="text-sm" style={{ color: 'var(--ij-text-primary)', lineHeight: 1.55 }}>{interpretacion}</p>
      </div>
    </div>
  )
}
