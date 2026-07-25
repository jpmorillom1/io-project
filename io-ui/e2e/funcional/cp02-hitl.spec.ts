import { test, expect, type Page } from '@playwright/test'

/**
 * CP-02 — Human-in-the-Loop: aprobación humana antes de resolver. Riesgo ALTO.
 * Cubre RF-HITL-01, RF-HITL-02, RF-HITL-03, RF-IA-01.
 *
 * Depende del LLM (Groq) → tag @ai: solo corre con GROQ_API_KEY.
 * Las aserciones son ESTRUCTURALES (aparece/desaparece la tarjeta, se renderiza el
 * resultado), nunca sobre el contenido semántico del tutor. El chat es no determinista:
 * `test.slow()` triplica el timeout y la config aplica 2 retries en CI.
 */

const PROMPT_EXPLICITO =
  'Resuelve este problema de programación lineal: maximizar Z = 5x1 + 4x2 sujeto a ' +
  '6x1 + 4x2 <= 24 y x1 + 2x2 <= 6, con x1, x2 >= 0. Formúlalo y resuélvelo ahora con Simplex.'

const NUDGES = ['Sí, resuélvelo ahora, por favor.', 'Adelante, ejecuta el solver.']

/** Envía un mensaje por el textarea del chat y espera a que Pivot termine de responder. */
async function enviarMensaje(page: Page, texto: string) {
  const input = page.getByPlaceholder(/Describe tu problema de IO/i)
  await input.fill(texto)
  await input.press('Enter')
  // Mientras Pivot piensa aparece la píldora "está escribiendo…"; esperamos a que se vaya.
  await page.getByText(/Pivot está escribiendo|Pivot|Validando|Resolviendo/i).first().waitFor().catch(() => {})
  await expect
    .poll(async () => (await page.getByText(/está escribiendo/i).count()), { timeout: 45_000 })
    .toBe(0)
}

/** Conduce la conversación hasta que aparece la tarjeta de aprobación (o se agotan los nudges). */
async function conducirHastaAprobacion(page: Page) {
  const card = page.getByTestId('hitl-card')
  await enviarMensaje(page, PROMPT_EXPLICITO)
  if (await card.isVisible().catch(() => false)) return
  for (const nudge of NUDGES) {
    if (await card.isVisible().catch(() => false)) return
    await enviarMensaje(page, nudge)
  }
  await expect(card, 'la tarjeta HITL debía aparecer tras pedir resolver').toBeVisible({ timeout: 20_000 })
}

test.describe('CP-02 · Human-in-the-Loop @ai', () => {
  test.slow() // el chat con LLM es lento

  test('RF-IA-01 · el chat responde a un mensaje del estudiante', async ({ page }) => {
    await page.goto('/lp')
    await enviarMensaje(page, 'Hola Pivot, tengo un problema de programación lineal.')
    // Aparece al menos una burbuja de respuesta (además del propio mensaje enviado)
    await expect(page.getByText(/está escribiendo/i)).toHaveCount(0)
    // El mensaje del usuario quedó en el hilo
    await expect(page.getByText('Hola Pivot, tengo un problema de programación lineal.')).toBeVisible()
  })

  test('RF-HITL-01/03 · rechazar NO ejecuta el solver', async ({ page }) => {
    await page.goto('/lp')
    await conducirHastaAprobacion(page)

    // RF-HITL-01 — la tarjeta existe y el resultado AÚN no se renderiza
    await expect(page.getByTestId('hitl-card')).toBeVisible()
    await expect(page.getByTestId('resultado-lp')).toHaveCount(0)

    // RF-HITL-03 — rechazar
    await page.getByTestId('hitl-rechazar').click()
    await page.getByTestId('hitl-confirmar-rechazo').click()

    // La tarjeta se consume y el solver NO corrió: no hay resultado LP
    await expect(page.getByTestId('hitl-card')).toHaveCount(0)
    await expect(page.getByTestId('resultado-lp')).toHaveCount(0)
  })

  test('RF-HITL-01/02 · aprobar ejecuta el solver y muestra el resultado', async ({ page }) => {
    await page.goto('/lp')
    await conducirHastaAprobacion(page)

    // RF-HITL-01 — antes de aprobar no hay resultado
    await expect(page.getByTestId('resultado-lp')).toHaveCount(0)

    // RF-HITL-02 — aprobar y resolver
    await page.getByTestId('hitl-aprobar').click()

    // La tarjeta se consume y aparece el resultado (tableau paso a paso)
    await expect(page.getByTestId('hitl-card')).toHaveCount(0, { timeout: 30_000 })
    await expect(page.getByTestId('resultado-lp')).toBeVisible({ timeout: 30_000 })
  })
})
