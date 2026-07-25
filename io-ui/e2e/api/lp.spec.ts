import { test, expect } from '../fixtures/test-base'
import {
  LP_SIMPLEX, LP_SIMPLEX_OPTIMO,
  LP_GRAN_M, LP_GRAN_M_OPTIMO,
  LP_INFACTIBLE, LP_NO_ACOTADO, LP_MALFORMADO,
} from '../fixtures/datos-io'

/**
 * CP-01 — Resolución de un modelo LP correcto (núcleo). Riesgo ALTO.
 * Cubre RF-LP-01, RF-LP-02, RF-LP-05, RF-LP-06, RF-LP-07.
 */
test.describe('CP-01 · LP', () => {
  test('PW-LP-01 · Simplex ≤ → OPTIMO con post-óptimo y steps @lp', async ({ api }) => {
    const res = await api.post('/lp/simplex', { data: LP_SIMPLEX })
    expect(res.status()).toBe(200)
    const body = await res.json()

    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorOptimo).toBeCloseTo(LP_SIMPLEX_OPTIMO, 1)
    // RF-LP-05 — análisis post-óptimo completo
    expect(body.solution).toHaveProperty('valores')
    expect(body.solution).toHaveProperty('holguras')
    expect(body.solution).toHaveProperty('preciosSombra')
    expect(body.solution).toHaveProperty('rangosSensibilidad')
    // RF-LP-06 — procedimiento paso a paso
    expect(Array.isArray(body.steps)).toBe(true)
    expect(body.steps.length).toBeGreaterThan(0)
  })

  test('PW-LP-02 · Gran M con GEQ respeta x1≥4 → (4,0) Z=20 @lp', async ({ api }) => {
    const res = await api.post('/lp/gran-m', { data: LP_GRAN_M })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorOptimo).toBeCloseTo(LP_GRAN_M_OPTIMO, 1)
  })

  test('PW-LP-05 · modelo infactible → INFACTIBLE, sin excepción (RF-LP-07) @lp', async ({ api }) => {
    const res = await api.post('/lp/gran-m', { data: LP_INFACTIBLE })
    expect(res.status()).toBe(200) // resultado válido, NO error HTTP
    const body = await res.json()
    expect(body.status).toBe('INFACTIBLE')
    expect(body.solution).toBeNull()
  })

  test('CP-01/X2 · modelo no acotado → NO_ACOTADO @lp', async ({ api }) => {
    const res = await api.post('/lp/simplex', { data: LP_NO_ACOTADO })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('NO_ACOTADO')
  })

  test('CP-01/X3 · objetivo con nº de coeficientes ≠ nº de variables → HTTP 400 @lp', async ({ api }) => {
    const res = await api.post('/lp/simplex', { data: LP_MALFORMADO })
    expect(res.status()).toBe(400)
    const body = await res.json()
    // El mensaje de error NO debe exponer trazas internas
    expect(JSON.stringify(body)).not.toMatch(/Exception|at jpap\.dev|\.java:/)
  })
})
