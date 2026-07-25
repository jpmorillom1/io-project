import { test, expect } from '../fixtures/test-base'
import { INV_EOQ, INV_EOQ_QOPTIMO, INV_POQ_INVALIDO } from '../fixtures/datos-io'

/**
 * CP-05 (parte Inventarios) — EOQ. Cubre RF-IN-01, RF-IN-02.
 */
test.describe('CP-05 · Inventarios', () => {
  test('PW-IN-01/02 · EOQ básico → Q*=√(2DK/H) y costos desglosados @inventario', async ({ api }) => {
    const res = await api.post('/inventario/eoq-basico', { data: INV_EOQ })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.cantidadOptima).toBeCloseTo(INV_EOQ_QOPTIMO, 1)
    // RF-IN-02 — desglose de costos y política
    expect(body.solution).toHaveProperty('costoTotalAnual')
    expect(body.solution).toHaveProperty('costoOrdenarAnual')
    expect(body.solution).toHaveProperty('costoMantenerAnual')
    expect(body.solution).toHaveProperty('numeroPedidos')
  })

  test('CP-05/X2 · POQ con P ≤ D → HTTP 400 @inventario', async ({ api }) => {
    const res = await api.post('/inventario/produccion-economica', { data: INV_POQ_INVALIDO })
    expect(res.status()).toBe(400)
  })
})
