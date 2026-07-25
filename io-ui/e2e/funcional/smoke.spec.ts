import { test, expect } from '@playwright/test'

/**
 * PW-SMOKE — la SPA carga y responde. Cubre RNF-REL-01 (ambiente disponible).
 * Recorre los 6 workspaces por sus rutas para confirmar que ninguno rompe al montar.
 */
const RUTAS = ['/', '/lp', '/transporte', '/redes', '/pl-entera', '/inventario', '/dinamica']

test.describe('Smoke de disponibilidad', () => {
  test('PW-SMOKE · la home carga con el asistente Pivot', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/.+/)
    // El panel del chat monta el asistente Pivot en toda la app
    await expect(page.getByText('Pivot').first()).toBeVisible()
    // El input del chat existe
    await expect(page.getByPlaceholder(/Describe tu problema de IO/i)).toBeVisible()
  })

  for (const ruta of RUTAS) {
    test(`PW-SMOKE · el workspace ${ruta} monta sin error`, async ({ page }) => {
      const erroresConsola: string[] = []
      page.on('pageerror', e => erroresConsola.push(e.message))
      await page.goto(ruta)
      // El chat (siempre visible) sigue presente → la ruta montó sin romperse
      await expect(page.getByPlaceholder(/Describe tu problema de IO/i)).toBeVisible()
      expect(erroresConsola, `errores de página en ${ruta}`).toEqual([])
    })
  }
})
