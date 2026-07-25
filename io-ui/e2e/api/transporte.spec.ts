import { test, expect } from '../fixtures/test-base'
import {
  TRANSPORTE, TRANSPORTE_OPTIMO,
  TRANSPORTE_DESBALANCEADO, TRANSPORTE_MALFORMADO,
} from '../fixtures/datos-io'

/**
 * CP-04 — Óptimo de transporte por MODI. Riesgo MEDIO.
 * Cubre RF-TR-01, RF-TR-02, RF-TR-03.
 */
test.describe('CP-04 · Transporte', () => {
  test('PW-TR-02 · MODI → costoTotal óptimo + comparativa de las 3 iniciales @transporte', async ({ api }) => {
    const res = await api.post('/transporte/modi', { data: TRANSPORTE })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.costoTotal).toBeCloseTo(TRANSPORTE_OPTIMO, 1)
    expect(Array.isArray(body.solution.comparativaInicial)).toBe(true)
    expect(body.solution.comparativaInicial.length).toBe(3) // NW, CostoMin, Vogel
  })

  test('PW-TR-01 · soluciones iniciales por separado son factibles @transporte', async ({ api }) => {
    for (const ruta of ['esquina-noroeste', 'costo-minimo', 'vogel']) {
      const res = await api.post(`/transporte/${ruta}`, { data: TRANSPORTE })
      expect(res.status(), ruta).toBe(200)
      const body = await res.json()
      expect(body.status, ruta).toBe('OPTIMO')
      expect(body.solution.costoTotal, ruta).toBeGreaterThan(0)
    }
  })

  test('PW-TR-03 · desbalance Σoferta≠Σdemanda → balancea y resuelve @transporte', async ({ api }) => {
    const res = await api.post('/transporte/modi', { data: TRANSPORTE_DESBALANCEADO })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO') // no es error: el backend balancea
  })

  test('CP-04/X1 · matriz de dimensiones incoherentes → HTTP 400 @transporte', async ({ api }) => {
    const res = await api.post('/transporte/modi', { data: TRANSPORTE_MALFORMADO })
    expect(res.status()).toBe(400)
  })
})
