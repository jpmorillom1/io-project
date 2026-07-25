import { test, expect } from '../fixtures/test-base'
import { ENTERA, ENTERA_OPTIMO, ENTERA_RELAJACION } from '../fixtures/datos-io'

/**
 * PL Entera — PW-EN-01/02. Cubre RF-EN-01, RF-EN-02.
 */
test.describe('PL Entera', () => {
  test('PW-EN-01 · Branch & Bound → óptimo entero = 20 en (4,0) @entera', async ({ api }) => {
    const res = await api.post('/entera/branch-and-bound', { data: ENTERA })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorOptimo).toBeCloseTo(ENTERA_OPTIMO, 1)
  })

  test('PW-EN-02 · salida expone brecha de integralidad y árbol de nodos @entera', async ({ api }) => {
    const res = await api.post('/entera/branch-and-bound', { data: ENTERA })
    const body = await res.json()
    // valorRelajacion (raíz) vs valorOptimo (entero) → brecha de integralidad
    expect(body.solution.valorRelajacion).toBeCloseTo(ENTERA_RELAJACION, 1)
    expect(body.solution.valorRelajacion).toBeGreaterThanOrEqual(body.solution.valorOptimo)
    expect(body.solution).toHaveProperty('nodosExplorados')
    expect(Array.isArray(body.steps)).toBe(true)
    expect(body.steps.length).toBeGreaterThan(0)
  })
})
