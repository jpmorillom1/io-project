import { useState, type CSSProperties } from 'react'
import { Button } from '@/components/ui/button'
import { Check, X, ShieldQuestion } from 'lucide-react'
import type {
  SolicitudAprobacion, ModeloLP, ModeloTransporte, ModeloRed, ModeloEntero, MetodoResolucion,
  MetodoTransporte, MetodoRed, TipoRestriccion, TipoVariable,
} from '@/types/io'

interface Props {
  solicitud: SolicitudAprobacion
  onDecidir: (aprobado: boolean, comentario: string | null) => void
  disabled?: boolean
}

const METODO_LABEL: Record<MetodoResolucion, string> = {
  SIMPLEX: 'Simplex estándar',
  GRAN_M: 'Gran M',
  DOS_FASES: 'Dos Fases',
  GRAFICO: 'Método gráfico',
  TRANSPORTE: 'Transporte',
  REDES: 'Redes',
  BRANCH_AND_BOUND: 'Branch & Bound',
}

const TIPO_VAR_LABEL: Record<TipoVariable, string> = {
  ENTERA: 'entera',
  BINARIA: 'binaria',
  CONTINUA: 'continua',
}

const METODO_TRANSPORTE_LABEL: Record<MetodoTransporte, string> = {
  ESQUINA_NOROESTE: 'Esquina Noroeste',
  COSTO_MINIMO: 'Costo Mínimo',
  VOGEL: 'Vogel (VAM)',
  MODI: 'MODI (óptimo)',
}

const METODO_RED_LABEL: Record<MetodoRed, string> = {
  DIJKSTRA: 'Dijkstra',
  KRUSKAL: 'Kruskal',
  EDMONDS_KARP: 'Edmonds-Karp',
  FLUJO_COSTO_MINIMO: 'Flujo de costo mínimo',
  ASIGNACION: 'Asignación',
}

const SIGNO: Record<TipoRestriccion, string> = { LEQ: '≤', GEQ: '≥', EQ: '=' }

function formatearExpresion(coeficientes: number[], variables: string[]): string {
  return coeficientes
    .map((c, i) => {
      const abs = Math.abs(c)
      const coef = abs === 1 ? '' : `${abs}`
      const termino = `${coef}${variables[i] ?? `x${i + 1}`}`
      if (i === 0) return c < 0 ? `-${termino}` : termino
      return `${c < 0 ? '- ' : '+ '}${termino}`
    })
    .join(' ')
}

function lineasModelo(modelo: ModeloLP): string[] {
  const obj = `${modelo.objetivo.tipo === 'MAXIMIZAR' ? 'MAX' : 'MIN'} Z = ${formatearExpresion(modelo.objetivo.coeficientes, modelo.variables)}`
  const restricciones = modelo.restricciones.map(
    r => `${formatearExpresion(r.coeficientes, modelo.variables)} ${SIGNO[r.tipo]} ${r.rhs}`
  )
  return [obj, ...restricciones]
}

/** Tabla oferta/demanda/costos de un modelo de transporte para la tarjeta de aprobación. */
function MatrizTransporte({ modelo }: { modelo: ModeloTransporte }) {
  const celda: CSSProperties = {
    border: '1px solid var(--ij-border)',
    padding: '2px 6px',
    textAlign: 'center',
    whiteSpace: 'nowrap',
  }
  const cabecera: CSSProperties = { ...celda, color: 'var(--ij-teal)', fontWeight: 600 }

  return (
    <table style={{ borderCollapse: 'collapse', fontSize: '12px' }}>
      <thead>
        <tr>
          <th style={cabecera}></th>
          {modelo.destinos.map((d, j) => (
            <th key={j} style={cabecera}>{d}</th>
          ))}
          <th style={cabecera}>Oferta</th>
        </tr>
      </thead>
      <tbody>
        {modelo.origenes.map((o, i) => (
          <tr key={i}>
            <td style={cabecera}>{o}</td>
            {modelo.destinos.map((_, j) => (
              <td key={j} style={celda}>{modelo.costos[i]?.[j]}</td>
            ))}
            <td style={{ ...celda, fontWeight: 600 }}>{modelo.oferta[i]}</td>
          </tr>
        ))}
        <tr>
          <td style={cabecera}>Demanda</td>
          {modelo.demanda.map((d, j) => (
            <td key={j} style={{ ...celda, fontWeight: 600 }}>{d}</td>
          ))}
          <td style={celda}></td>
        </tr>
      </tbody>
    </table>
  )
}

/** Resumen del grafo/asignación de un modelo de redes para la tarjeta de aprobación. */
function ResumenRed({ modelo }: { modelo: ModeloRed }) {
  const celda: CSSProperties = {
    border: '1px solid var(--ij-border)',
    padding: '2px 6px',
    textAlign: 'center',
    whiteSpace: 'nowrap',
  }
  const cabecera: CSSProperties = { ...celda, color: 'var(--ij-teal)', fontWeight: 600 }

  if (modelo.metodo === 'ASIGNACION') {
    const agentes = modelo.agentes ?? []
    const tareas = modelo.tareas ?? []
    return (
      <table style={{ borderCollapse: 'collapse', fontSize: '12px' }}>
        <thead>
          <tr>
            <th style={cabecera}></th>
            {tareas.map((t, j) => (
              <th key={j} style={cabecera}>{t}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {agentes.map((a, i) => (
            <tr key={i}>
              <td style={cabecera}>{a}</td>
              {tareas.map((_, j) => (
                <td key={j} style={celda}>{modelo.matrizCostos?.[i]?.[j]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    )
  }

  const valorArista = (a: NonNullable<ModeloRed['aristas']>[number]) => {
    switch (modelo.metodo) {
      case 'EDMONDS_KARP': return `cap ${a.capacidad ?? 0}`
      case 'FLUJO_COSTO_MINIMO': return `cap ${a.capacidad ?? 0} · $${a.costo ?? 0}`
      default: return `peso ${a.peso ?? 0}`
    }
  }
  const flecha = modelo.metodo === 'KRUSKAL' ? ' — ' : ' → '

  return (
    <>
      {(modelo.aristas ?? []).map((a, i) => (
        <p key={i}>
          {a.origen}{flecha}{a.destino} : {valorArista(a)}
        </p>
      ))}
      {(modelo.fuente || modelo.sumidero) && (
        <p style={{ color: 'var(--ij-teal)' }}>
          {modelo.fuente && `Fuente: ${modelo.fuente}`}
          {modelo.fuente && modelo.sumidero && ' · '}
          {modelo.sumidero && `Sumidero: ${modelo.sumidero}`}
        </p>
      )}
    </>
  )
}

/** Resumen de un modelo de PL Entera (relajación LP + tipos) para la tarjeta de aprobación. */
function ResumenEntero({ modelo }: { modelo: ModeloEntero }) {
  const rel = modelo.relajacion
  const lineas = lineasModelo(rel)
  const tipos = rel.variables
    .map((v, i) => `${v}: ${TIPO_VAR_LABEL[modelo.tiposVariable[i] ?? 'ENTERA']}`)
    .join(' · ')
  return (
    <>
      {lineas.map((linea, i) => (
        <p key={i} className={i === 0 ? 'font-semibold' : undefined}>{linea}</p>
      ))}
      <p style={{ color: 'var(--ij-teal)' }}>{tipos}</p>
    </>
  )
}

/**
 * Tarjeta Human-in-the-Loop: el tutor quiere ejecutar un solver y espera la
 * decisión del estudiante. Rechazar pide un comentario que re-alimenta al tutor.
 */
export function ApprovalCard({ solicitud, onDecidir, disabled }: Props) {
  const [rechazando, setRechazando] = useState(false)
  const [comentario, setComentario] = useState('')

  const esTransporte = solicitud.metodo === 'TRANSPORTE'
  const modeloT = esTransporte ? (solicitud.modelo as ModeloTransporte) : null
  const esRed = solicitud.metodo === 'REDES'
  const modeloR = esRed ? (solicitud.modelo as ModeloRed) : null
  const esEntero = solicitud.metodo === 'BRANCH_AND_BOUND'
  const modeloE = esEntero ? (solicitud.modelo as ModeloEntero) : null
  const etiquetaMetodo = modeloT
    ? `Transporte · ${METODO_TRANSPORTE_LABEL[modeloT.metodo]}`
    : modeloR
      ? `Redes · ${METODO_RED_LABEL[modeloR.metodo]}`
      : METODO_LABEL[solicitud.metodo]

  return (
    <div
      className="rounded-[4px] p-3 space-y-2.5"
      style={{
        background: 'var(--ij-bg-hover)',
        border: '1px solid var(--ij-teal)',
      }}
    >
      <div className="flex items-center gap-2">
        <ShieldQuestion className="h-4 w-4 shrink-0" style={{ color: 'var(--ij-teal)' }} />
        <p className="text-xs font-semibold" style={{ color: 'var(--ij-text-default)' }}>
          Pivot quiere resolver con{' '}
          <span style={{ color: 'var(--ij-teal)' }}>{etiquetaMetodo}</span>
        </p>
      </div>

      <div
        className="rounded-[4px] px-3 py-2 space-y-0.5 overflow-x-auto"
        style={{
          background: 'var(--ij-bg-secondary)',
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: '12px',
          color: 'var(--ij-text-default)',
        }}
      >
        {modeloT ? (
          <MatrizTransporte modelo={modeloT} />
        ) : modeloR ? (
          <ResumenRed modelo={modeloR} />
        ) : modeloE ? (
          <ResumenEntero modelo={modeloE} />
        ) : (
          lineasModelo(solicitud.modelo as ModeloLP).map((linea, i) => (
            <p key={i} className={i === 0 ? 'font-semibold' : undefined}>
              {linea}
            </p>
          ))
        )}
      </div>

      {!rechazando ? (
        <div className="flex gap-2">
          <Button
            size="sm"
            className="flex-1 rounded-[4px]"
            disabled={disabled}
            onClick={() => onDecidir(true, null)}
          >
            <Check className="h-3.5 w-3.5 mr-1" />
            Aprobar y resolver
          </Button>
          <Button
            size="sm"
            variant="outline"
            className="flex-1 rounded-[4px]"
            disabled={disabled}
            onClick={() => setRechazando(true)}
          >
            <X className="h-3.5 w-3.5 mr-1" />
            Rechazar
          </Button>
        </div>
      ) : (
        <div className="space-y-2">
          <textarea
            value={comentario}
            onChange={e => setComentario(e.target.value)}
            placeholder="¿Qué hay que corregir del modelo? (opcional)"
            rows={2}
            autoFocus
            className="w-full resize-none rounded-[4px] px-3 py-2 placeholder:text-[var(--ij-text-muted)]"
            style={{
              fontSize: '12px',
              background: 'var(--ij-bg-secondary)',
              color: 'var(--ij-text-default)',
              border: '1px solid var(--ij-border)',
              outline: 'none',
            }}
          />
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="destructive"
              className="flex-1 rounded-[4px]"
              disabled={disabled}
              onClick={() => onDecidir(false, comentario.trim() || null)}
            >
              Confirmar rechazo
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="rounded-[4px]"
              disabled={disabled}
              onClick={() => setRechazando(false)}
            >
              Volver
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
