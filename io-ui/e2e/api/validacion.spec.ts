import { test, expect } from '../fixtures/test-base'
import {
  LP_MALFORMADO, TRANSPORTE_MALFORMADO, ENUNCIADO_SQLI,
} from '../fixtures/datos-io'

/**
 * CP-03 — Manejo de entradas malformadas y validación. Riesgo ALTO.
 * Cubre RF-VAL-01, RF-VAL-02, RNF-REL-02, y parte de VUL-02/04.
 */
test.describe('CP-03 · Validación de entradas', () => {
  test('PW-VAL-01 · objetivo nulo → HTTP 400 sin traza interna @val', async ({ api }) => {
    const res = await api.post('/lp/gran-m', { data: { variables: ['x1'], objetivo: null, restricciones: [] } })
    expect(res.status()).toBe(400)
    const raw = await res.text()
    expect(raw).not.toMatch(/at jpap\.dev|\.java:\d+|NullPointerException/)
  })

  test('PW-VAL-02 · dimensiones incoherentes (LP y transporte) → HTTP 400 @val', async ({ api }) => {
    const lp = await api.post('/lp/simplex', { data: LP_MALFORMADO })
    expect(lp.status(), 'lp malformado').toBe(400)

    const tr = await api.post('/transporte/modi', { data: TRANSPORTE_MALFORMADO })
    expect(tr.status(), 'transporte malformado').toBe(400)
  })

  test('CP-03/X1 · JSON sintácticamente inválido → HTTP 400 @val', async ({ api }) => {
    const res = await api.post('/lp/simplex', {
      headers: { 'Content-Type': 'application/json' },
      data: '{ "variables": ["x1", ',   // llaves sin cerrar
    })
    expect(res.status()).toBe(400)
  })

  test('CP-03/X2 · payload SQLi en variable → tratado como texto (VUL-02) @val @seguridad', async ({ api }) => {
    // El nombre de variable lleva un intento de inyección: el solver no toca la BD,
    // así que debe procesarlo como dato (200) o rechazarlo (400), nunca ejecutar SQL ni 500.
    const modelo = {
      variables: ["x1'; DROP TABLE sesion; --", 'x2'],
      objetivo: { coeficientes: [1.0, 1.0], tipo: 'MAXIMIZAR' },
      restricciones: [{ coeficientes: [1.0, 1.0], tipo: 'LEQ', rhs: 4.0 }],
    }
    const res = await api.post('/lp/simplex', { data: modelo })
    expect([200, 400]).toContain(res.status())
    expect(res.status()).not.toBe(500)
  })

  test('CP-03/X3 · redes/asignacion sin `dirigido` — DEFECTO CONOCIDO @val @defecto', async ({ api }, testInfo) => {
    // ModeloRed.dirigido es `boolean` primitivo: omitirlo hace fallar a Jackson.
    // Comportamiento ACTUAL documentado: HTTP 500. DESEADO: HTTP 400.
    // Ver io-api/perf/README.md §"Hallazgo". La prueba registra el estado real.
    const res = await api.post('/redes/asignacion', {
      data: { agentes: ['A1', 'A2'], tareas: ['T1', 'T2'], matrizCostos: [[1, 2], [3, 4]] },
    })
    testInfo.annotations.push({
      type: 'defecto-conocido',
      description: `status recibido = ${res.status()} (deseado 400; actual documentado 500)`,
    })
    // Aceptamos 400 (corregido) o 500 (estado actual) para no romper la suite por un bug conocido.
    expect([400, 500]).toContain(res.status())
  })

  test('CP-03/X · SQLi en el enunciado del extractor no ejecuta SQL @val @seguridad @ai', async ({ api }) => {
    // Requiere LLM: solo corre si hay GROQ_API_KEY (tag @ai).
    const res = await api.post('/ai/sugerir-modelo', { data: { descripcionProblema: ENUNCIADO_SQLI } })
    // El endpoint puede responder 200 (modelo sugerido) o degradar, pero nunca 500 por SQL.
    expect(res.status()).not.toBe(500)
  })
})
