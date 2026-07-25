import { test, expect } from '../fixtures/test-base'
import {
  PD_MOCHILA, PD_MOCHILA_OPTIMO,
  PD_RUTA, PD_RUTA_OPTIMO, PD_RUTA_SIN_CAMINO,
} from '../fixtures/datos-io'

/**
 * CP-05 (parte PD) — Programación Dinámica. Cubre RF-PD-01, RF-PD-02, RF-PD-03.
 */
test.describe('CP-05 · Programación Dinámica', () => {
  test('PW-PD-01 · Mochila 0/1 → valor óptimo = 7 @dinamica', async ({ api }) => {
    const res = await api.post('/dinamica/mochila', { data: PD_MOCHILA })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorOptimo).toBeCloseTo(PD_MOCHILA_OPTIMO, 1)
  })

  test('PW-PD-02 · salida expone los 7 elementos del modelo de PD @dinamica', async ({ api }) => {
    const res = await api.post('/dinamica/ruta-etapas', { data: PD_RUTA })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorOptimo).toBeCloseTo(PD_RUTA_OPTIMO, 1)
    // Los 7 elementos del modelo de PD
    for (const campo of [
      'definicionEtapas', 'definicionEstados', 'definicionDecisiones',
      'funcionRecurrencia', 'principioOptimalidad', 'tablas', 'politicaOptima',
    ]) {
      expect(body.solution, campo).toHaveProperty(campo)
    }
    expect(body.solution.rutaOptima).toBeTruthy() // solo RUTA_ETAPAS
  })

  test('PW-PD-03/CP-05-X1 · sin camino → INFACTIBLE y ningún Infinity en el JSON @dinamica', async ({ api }) => {
    const res = await api.post('/dinamica/ruta-etapas', { data: PD_RUTA_SIN_CAMINO })
    expect(res.status()).toBe(200)
    const raw = await res.text()
    // RF-PD-03 / RNF-REL-03 — el token Infinity NO es JSON válido y no debe salir
    expect(raw).not.toContain('Infinity')
    expect(raw).not.toContain('NaN')
    const body = JSON.parse(raw) // reparseo: confirma que es JSON válido
    expect(body.status).toBe('INFACTIBLE')
    expect(body.solution).toBeNull()
  })
})
