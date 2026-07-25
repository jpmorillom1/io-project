import { defineConfig, devices } from '@playwright/test'

/**
 * Configuración de las pruebas E2E de Ío.
 *
 * Dos tipos de spec conviven bajo el mismo runner:
 *   - `api/`       → pruebas funcionales por `request` context (REST directo al backend).
 *                    Solo necesitan el backend en `API_BASE_URL`.
 *   - `funcional/` → pruebas de UI que conducen el frontend en `E2E_BASE_URL`.
 *                    En local, `webServer` levanta Vite automáticamente.
 *
 * Variables de entorno:
 *   API_BASE_URL   base de la API REST      (default http://localhost:8080/api/v1)
 *   E2E_BASE_URL   base de la SPA           (default http://localhost:5173)
 *                  Si se define, NO se arranca el webServer (se asume ya desplegada).
 *   GROQ_API_KEY   si falta, se omiten los specs @ai (chat con LLM).
 */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1'
const E2E_BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const arrancarWebServer = !process.env.E2E_BASE_URL

export default defineConfig({
  testDir: '.',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 30_000,
  expect: { timeout: 10_000 },

  reporter: [
    ['list'],
    ['html', { outputFolder: 'report/html', open: 'never' }],
    ['junit', { outputFile: 'report/results.xml' }],
  ],

  use: {
    baseURL: E2E_BASE_URL,
    // Base para las peticiones REST del `request` fixture (ver fixtures/test-base.ts).
    // Se expone también como variable global para los specs de API.
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  metadata: { apiBaseUrl: API_BASE_URL },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Solo se arranca el front cuando NO se apunta a un despliegue existente.
  webServer: arrancarWebServer
    ? {
        command: 'npm --prefix .. run dev -- --port 5173 --strictPort',
        url: E2E_BASE_URL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      }
    : undefined,
})
