import { test, expect } from '../fixtures/test-base'
import {
  RED_DIJKSTRA, RED_DIJKSTRA_OPTIMO,
  RED_KRUSKAL, RED_KRUSKAL_OPTIMO,
  RED_EDMONDS_KARP, RED_EDMONDS_KARP_OPTIMO,
  RED_ASIGNACION, RED_ASIGNACION_OPTIMO,
  RED_DESCONEXA, RED_PESO_NEGATIVO,
} from '../fixtures/datos-io'

/**
 * Redes — PW-RE-01..05. Cubre RF-RE-01..05.
 * El frontend de redes existe, pero se prueba por REST (más determinista).
 */
test.describe('Redes', () => {
  test('PW-RE-01 · Dijkstra → ruta más corta = 10 @redes', async ({ api }) => {
    const res = await api.post('/redes/dijkstra', { data: RED_DIJKSTRA })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorObjetivo).toBeCloseTo(RED_DIJKSTRA_OPTIMO, 1)
  })

  test('PW-RE-02 · Kruskal → MST peso = 6 @redes', async ({ api }) => {
    const res = await api.post('/redes/kruskal', { data: RED_KRUSKAL })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorObjetivo).toBeCloseTo(RED_KRUSKAL_OPTIMO, 1)
  })

  test('PW-RE-03 · Edmonds-Karp → flujo máximo = 5 @redes', async ({ api }) => {
    const res = await api.post('/redes/edmonds-karp', { data: RED_EDMONDS_KARP })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorObjetivo).toBeCloseTo(RED_EDMONDS_KARP_OPTIMO, 1)
  })

  test('PW-RE-04 · Asignación → costo óptimo = 9 @redes', async ({ api }) => {
    const res = await api.post('/redes/asignacion', { data: RED_ASIGNACION })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('OPTIMO')
    expect(body.solution.valorObjetivo).toBeCloseTo(RED_ASIGNACION_OPTIMO, 1)
    expect(body.solution.asignacion).toBeTruthy()
  })

  test('PW-RE-05 · grafo desconexo (sumidero inalcanzable) → INFACTIBLE, sin excepción @redes', async ({ api }) => {
    const res = await api.post('/redes/dijkstra', { data: RED_DESCONEXA })
    expect(res.status()).toBe(200) // no es error HTTP
    const body = await res.json()
    expect(body.status).toBe('INFACTIBLE')
    expect(body.solution).toBeNull()
  })

  test('RE/X · peso negativo en Dijkstra → HTTP 400 (entrada inválida) @redes', async ({ api }) => {
    const res = await api.post('/redes/dijkstra', { data: RED_PESO_NEGATIVO })
    expect(res.status()).toBe(400)
  })
})
