import { type CSSProperties } from 'react'
import { formatNum } from '@/lib/utils'
import type { SolveResultInventario, SolucionInventario, MetodoInventario } from '@/types/io'

interface Props {
  resultado: SolveResultInventario
}

const MONO: CSSProperties = { fontFamily: "'JetBrains Mono', monospace" }

// Campos secundarios de la solución, en orden de lectura, con su etiqueta.
// (Q* y costo total van como titulares aparte.)
const CAMPOS: Array<{ key: keyof SolucionInventario; label: string }> = [
  { key: 'costoOrdenarAnual', label: 'Costo de ordenar anual' },
  { key: 'costoMantenerAnual', label: 'Costo de mantener anual' },
  { key: 'costoCompraAnual', label: 'Costo de compra anual (D·C)' },
  { key: 'costoFaltanteAnual', label: 'Costo de faltante anual' },
  { key: 'numeroPedidos', label: 'Número de pedidos (N)' },
  { key: 'tiempoCicloDias', label: 'Tiempo de ciclo (días)' },
  { key: 'nivelMaximoInventario', label: 'Nivel máximo de inventario' },
  { key: 'faltanteMaximo', label: 'Faltante máximo' },
  { key: 'puntoReorden', label: 'Punto de reorden (R)' },
  { key: 'demandaDiaria', label: 'Demanda diaria (d)' },
  { key: 'precioUnitarioOptimo', label: 'Precio unitario óptimo' },
]

/**
 * Tarjeta de resultado de Inventarios. Caja verde con Q* y el costo total como
 * titulares, una grilla con los campos secundarios del submodelo (solo non-null),
 * la comparativa de tramos (EOQ con descuentos) y la interpretación de la política.
 */
export function InventarioResultCard({ resultado }: Props) {
  const metodo: MetodoInventario | undefined = resultado.steps[0]?.datos.metodo

  if (resultado.status !== 'OPTIMO' || !resultado.solution) {
    return (
      <div className="rounded-[4px] p-4" style={{ background: 'rgba(255,82,99,0.08)', borderLeft: '2px solid var(--ij-red)' }}>
        <p className="font-semibold text-sm" style={{ color: 'var(--ij-red)' }}>{resultado.status}</p>
        <p className="mt-1 text-sm" style={{ color: 'var(--ij-red)', opacity: 0.85 }}>
          El modelo no pudo resolverse. Revisa los parámetros de entrada.
        </p>
      </div>
    )
  }

  const sol = resultado.solution
  const secundarios = CAMPOS.filter(c => {
    const v = sol[c.key]
    return typeof v === 'number' && v != null
  })

  return (
    <div
      className="rounded-[4px] p-4 space-y-4"
      style={{ background: 'rgba(106,171,116,0.1)', borderLeft: '2px solid var(--ij-green)' }}
    >
      <div className="flex flex-wrap gap-8">
        <div>
          <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600 }}>Cantidad óptima Q*</p>
          <p className="text-lg" style={{ ...MONO, color: 'var(--ij-green)' }}>{formatNum(sol.cantidadOptima)}</p>
        </div>
        <div>
          <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600 }}>Costo total anual</p>
          <p className="text-lg" style={{ ...MONO, color: 'var(--ij-green)' }}>{formatNum(sol.costoTotalAnual)}</p>
        </div>
      </div>

      {secundarios.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-x-6 gap-y-2">
          {secundarios.map(c => (
            <div key={c.key}>
              <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)' }}>{c.label}</p>
              <p className="text-sm" style={{ ...MONO, color: 'var(--ij-text-primary)' }}>
                {formatNum(sol[c.key] as number)}
              </p>
            </div>
          ))}
        </div>
      )}

      {metodo === 'EOQ_DESCUENTOS' && sol.comparativa && sol.comparativa.length > 0 && (
        <div>
          <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '6px' }}>
            Comparativa por tramo
          </p>
          <div className="overflow-x-auto">
            <table style={{ ...MONO, fontSize: '12px', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ color: 'var(--ij-text-secondary)' }}>
                  <th style={celdaCab}>Precio</th>
                  <th style={celdaCab}>Cantidad</th>
                  <th style={celdaCab}>Costo total</th>
                  <th style={celdaCab}>Factible</th>
                </tr>
              </thead>
              <tbody>
                {sol.comparativa.map((fila, i) => {
                  const ganador = fila.factible && fila.precioUnitario === sol.precioUnitarioOptimo
                  const color = ganador ? 'var(--ij-green)' : fila.factible ? 'var(--ij-text-primary)' : 'var(--ij-text-muted)'
                  return (
                    <tr key={i} style={{ background: ganador ? 'rgba(106,171,116,0.12)' : undefined }}>
                      <td style={{ ...celda, color }}>{formatNum(fila.precioUnitario)}</td>
                      <td style={{ ...celda, color }}>{formatNum(fila.cantidad)}</td>
                      <td style={{ ...celda, color, fontWeight: ganador ? 700 : 400 }}>{formatNum(fila.costoTotal)}</td>
                      <td style={{ ...celda, color }}>{fila.factible ? '✓' : '—'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div>
        <p style={{ fontSize: '11px', color: 'var(--ij-text-secondary)', fontWeight: 600, marginBottom: '4px' }}>
          Interpretación de la política
        </p>
        <p className="text-sm" style={{ color: 'var(--ij-text-primary)', lineHeight: 1.55 }}>
          {sol.interpretacionPolitica}
        </p>
      </div>
    </div>
  )
}

const celdaCab: CSSProperties = {
  border: '1px solid var(--ij-border)',
  padding: '3px 10px',
  textAlign: 'center',
  fontWeight: 600,
}
const celda: CSSProperties = {
  border: '1px solid var(--ij-border)',
  padding: '3px 10px',
  textAlign: 'center',
}
