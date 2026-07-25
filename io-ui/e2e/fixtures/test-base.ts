import { test as base, request, type APIRequestContext } from '@playwright/test'

/**
 * Fixture compartida. Añade `api`: un `APIRequestContext` apuntado a la base REST del
 * backend (`API_BASE_URL`), independiente del frontend. Los specs de `api/` lo usan para
 * pegarle directo a `/api/v1/...` sin arrancar la SPA.
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1'

type Fixtures = {
  api: APIRequestContext
}

export const test = base.extend<Fixtures>({
  api: async ({}, use) => {
    const ctx = await request.newContext({
      baseURL: API_BASE_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    })
    await use(ctx)
    await ctx.dispose()
  },
})

export const expect = test.expect

/** UUID v4 para acuñar sesionId en los specs de chat. */
export function nuevoSesionId(): string {
  return crypto.randomUUID()
}
