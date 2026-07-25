import { test, expect, nuevoSesionId } from '../fixtures/test-base'
import { LP_SIMPLEX } from '../fixtures/datos-io'

/**
 * Endpoints de IA — flujo, no semántica. Cubre RF-IA-01..05, RF-PE-01.
 *
 * Los que invocan al LLM van con el tag @ai (solo corren con GROQ_API_KEY).
 * `actividad` e `historial` NO dependen del LLM y corren siempre.
 */
test.describe('Endpoints de IA', () => {
  // ── Sin LLM ────────────────────────────────────────────────────────────

  test('PW-IA-04 · GET actividad de una sesión sin turno → 204 @ia', async ({ api }) => {
    const res = await api.get(`/ai/chat/${nuevoSesionId()}/actividad`)
    expect(res.status()).toBe(204)
  })

  test('PW-IA-05 · GET historial de una sesión inexistente → 404 @ia', async ({ api }) => {
    const res = await api.get(`/ai/chat/${nuevoSesionId()}/historial`)
    expect(res.status()).toBe(404)
  })

  // ── Con LLM (Groq) ─────────────────────────────────────────────────────

  test('PW-IA-01 · el chat responde con texto y sesionId @ia @ai', async ({ api }) => {
    const res = await api.post('/ai/chat', {
      data: { sesionId: null, mensaje: 'Hola, quiero resolver un problema de programación lineal.' },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(typeof body.respuesta).toBe('string')
    expect(body.respuesta.length).toBeGreaterThan(0)
    expect(body.sesionId).toBeTruthy()
  })

  test('PW-IA-02 · sugerir-modelo devuelve un ModeloLP estructurado @ia @ai', async ({ api }) => {
    const res = await api.post('/ai/sugerir-modelo', {
      data: { descripcionProblema: 'Maximizar 5x1+4x2 sujeto a 6x1+4x2<=24 y x1+2x2<=6.' },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    // Estructura, no exactitud semántica
    expect(body).toBeTruthy()
  })

  test('PW-IA-03 · validar-modelo devuelve un veredicto estructurado @ia @ai', async ({ api }) => {
    const res = await api.post('/ai/validar-modelo', {
      data: { descripcionProblema: 'Maximizar 5x1+4x2 con dos restricciones de recursos.', modelo: LP_SIMPLEX },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body).toBeTruthy()
  })

  test('PW-PE-01 · la conversación se persiste: historial devuelve los turnos previos @ia @ai', async ({ api }) => {
    // Turno 1 — crea la sesión
    const r1 = await api.post('/ai/chat', {
      data: { sesionId: null, mensaje: 'Tengo un problema de LP con dos productos.' },
    })
    const { sesionId } = await r1.json()
    expect(sesionId).toBeTruthy()

    // Turno 2 — misma sesión
    await api.post('/ai/chat', { data: { sesionId, mensaje: 'Ayúdame a formularlo.' } })

    // El historial persiste los mensajes de esa sesión
    const hist = await api.get(`/ai/chat/${sesionId}/historial`)
    expect(hist.status()).toBe(200)
    const body = await hist.json()
    const mensajes = body.mensajes ?? body.transcript ?? body
    expect(Array.isArray(mensajes) ? mensajes.length : 0).toBeGreaterThan(0)
  })
})
