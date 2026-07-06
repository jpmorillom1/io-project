import { useState } from 'react'
import type { ModeloRed, MetodoRed, Arista } from '@/types/io'

/**
 * Estado del formulario de Redes: mantiene TODOS los campos poblados (grafo y
 * asignación) para poder cambiar de método sin perder lo tecleado. Al enviar,
 * `aModeloRed` anula los campos que no corresponden al método (contrato del backend).
 */
export interface RedFormModelo {
  nodos: string[]
  aristas: Arista[]
  dirigido: boolean
  metodo: MetodoRed
  fuente: string
  sumidero: string
  agentes: string[]
  tareas: string[]
  matrizCostos: number[][]
}

// Ejemplo del API_CONTRACT: Dijkstra A→E con distancia 10.
const MODELO_EJEMPLO: RedFormModelo = {
  nodos: ['A', 'B', 'C', 'D', 'E'],
  aristas: [
    { origen: 'A', destino: 'B', peso: 4, capacidad: 4, costo: 1 },
    { origen: 'A', destino: 'C', peso: 2, capacidad: 2, costo: 1 },
    { origen: 'C', destino: 'B', peso: 1, capacidad: 1, costo: 1 },
    { origen: 'B', destino: 'D', peso: 5, capacidad: 5, costo: 1 },
    { origen: 'D', destino: 'E', peso: 2, capacidad: 2, costo: 1 },
  ],
  dirigido: true,
  metodo: 'DIJKSTRA',
  fuente: 'A',
  sumidero: 'E',
  agentes: ['A1', 'A2', 'A3'],
  tareas: ['T1', 'T2', 'T3'],
  matrizCostos: [
    [9, 2, 7],
    [6, 4, 3],
    [5, 8, 1],
  ],
}

const USA_GRAFO: Record<MetodoRed, boolean> = {
  DIJKSTRA: true,
  KRUSKAL: true,
  EDMONDS_KARP: true,
  FLUJO_COSTO_MINIMO: true,
  ASIGNACION: false,
}

/** Convierte el estado del formulario al `ModeloRed` que espera el backend. */
export function aModeloRed(f: RedFormModelo): ModeloRed {
  if (!USA_GRAFO[f.metodo]) {
    return {
      nodos: null, aristas: null, dirigido: null, metodo: f.metodo,
      fuente: null, sumidero: null,
      agentes: f.agentes, tareas: f.tareas, matrizCostos: f.matrizCostos,
    }
  }
  const esKruskal = f.metodo === 'KRUSKAL'
  return {
    nodos: f.nodos,
    aristas: f.aristas.map(a => limpiarArista(a, f.metodo)),
    dirigido: esKruskal ? false : f.dirigido,
    metodo: f.metodo,
    fuente: esKruskal ? null : f.fuente || null,
    sumidero: esKruskal ? null : f.sumidero || null,
    agentes: null, tareas: null, matrizCostos: null,
  }
}

/** Deja en la arista solo los campos que su método usa. */
function limpiarArista(a: Arista, metodo: MetodoRed): Arista {
  switch (metodo) {
    case 'DIJKSTRA':
    case 'KRUSKAL':
      return { origen: a.origen, destino: a.destino, peso: a.peso ?? 0, capacidad: null, costo: null }
    case 'EDMONDS_KARP':
      return { origen: a.origen, destino: a.destino, peso: null, capacidad: a.capacidad ?? 0, costo: null }
    default: // FLUJO_COSTO_MINIMO
      return { origen: a.origen, destino: a.destino, peso: null, capacidad: a.capacidad ?? 0, costo: a.costo ?? 0 }
  }
}

/** Rellena el formulario desde un `ModeloRed` (p.ej. el que propone el chat). */
export function desdeModeloRed(m: ModeloRed, base: RedFormModelo = MODELO_EJEMPLO): RedFormModelo {
  return {
    nodos: m.nodos ?? base.nodos,
    aristas: (m.aristas ?? base.aristas).map(a => ({
      origen: a.origen, destino: a.destino,
      peso: a.peso ?? 0, capacidad: a.capacidad ?? 0, costo: a.costo ?? 0,
    })),
    dirigido: m.dirigido ?? true,
    metodo: m.metodo,
    fuente: m.fuente ?? '',
    sumidero: m.sumidero ?? '',
    agentes: m.agentes ?? base.agentes,
    tareas: m.tareas ?? base.tareas,
    matrizCostos: (m.matrizCostos ?? base.matrizCostos).map(fila => [...fila]),
  }
}

/** Estado editable de un modelo de redes para el formulario. */
export function useRedForm(inicial: RedFormModelo = MODELO_EJEMPLO) {
  const [modelo, setModelo] = useState<RedFormModelo>(inicial)

  function setMetodo(metodo: MetodoRed) {
    setModelo(m => ({ ...m, metodo }))
  }

  function setDirigido(dirigido: boolean) {
    setModelo(m => ({ ...m, dirigido }))
  }

  function setFuente(fuente: string) {
    setModelo(m => ({ ...m, fuente }))
  }

  function setSumidero(sumidero: string) {
    setModelo(m => ({ ...m, sumidero }))
  }

  // ── nodos ──────────────────────────────────────────────────────────────────

  function agregarNodo() {
    setModelo(m => {
      let i = m.nodos.length + 1
      while (m.nodos.includes(`N${i}`)) i++
      return { ...m, nodos: [...m.nodos, `N${i}`] }
    })
  }

  function quitarNodo(idx: number) {
    setModelo(m => {
      if (m.nodos.length <= 2) return m
      const nombre = m.nodos[idx]
      return {
        ...m,
        nodos: m.nodos.filter((_, i) => i !== idx),
        aristas: m.aristas.filter(a => a.origen !== nombre && a.destino !== nombre),
        fuente: m.fuente === nombre ? '' : m.fuente,
        sumidero: m.sumidero === nombre ? '' : m.sumidero,
      }
    })
  }

  function renombrarNodo(idx: number, nombre: string) {
    setModelo(m => {
      const viejo = m.nodos[idx]
      const nodos = [...m.nodos]
      nodos[idx] = nombre
      return {
        ...m,
        nodos,
        aristas: m.aristas.map(a => ({
          ...a,
          origen: a.origen === viejo ? nombre : a.origen,
          destino: a.destino === viejo ? nombre : a.destino,
        })),
        fuente: m.fuente === viejo ? nombre : m.fuente,
        sumidero: m.sumidero === viejo ? nombre : m.sumidero,
      }
    })
  }

  // ── aristas ────────────────────────────────────────────────────────────────

  function agregarArista() {
    setModelo(m => ({
      ...m,
      aristas: [...m.aristas, {
        origen: m.nodos[0] ?? '',
        destino: m.nodos[1] ?? m.nodos[0] ?? '',
        peso: 0, capacidad: 0, costo: 0,
      }],
    }))
  }

  function quitarArista(idx: number) {
    setModelo(m => ({ ...m, aristas: m.aristas.filter((_, i) => i !== idx) }))
  }

  function setArista(idx: number, cambios: Partial<Arista>) {
    setModelo(m => {
      const aristas = [...m.aristas]
      aristas[idx] = { ...aristas[idx], ...cambios }
      return { ...m, aristas }
    })
  }

  // ── asignación ─────────────────────────────────────────────────────────────

  function setCosto(i: number, j: number, valor: number) {
    setModelo(m => {
      const matrizCostos = m.matrizCostos.map(fila => [...fila])
      matrizCostos[i][j] = valor
      return { ...m, matrizCostos }
    })
  }

  function setNombreAgente(i: number, nombre: string) {
    setModelo(m => {
      const agentes = [...m.agentes]
      agentes[i] = nombre
      return { ...m, agentes }
    })
  }

  function setNombreTarea(j: number, nombre: string) {
    setModelo(m => {
      const tareas = [...m.tareas]
      tareas[j] = nombre
      return { ...m, tareas }
    })
  }

  function agregarAgente() {
    setModelo(m => ({
      ...m,
      agentes: [...m.agentes, `A${m.agentes.length + 1}`],
      matrizCostos: [...m.matrizCostos, m.tareas.map(() => 0)],
    }))
  }

  function quitarAgente() {
    setModelo(m => (m.agentes.length <= 1 ? m : {
      ...m,
      agentes: m.agentes.slice(0, -1),
      matrizCostos: m.matrizCostos.slice(0, -1),
    }))
  }

  function agregarTarea() {
    setModelo(m => ({
      ...m,
      tareas: [...m.tareas, `T${m.tareas.length + 1}`],
      matrizCostos: m.matrizCostos.map(fila => [...fila, 0]),
    }))
  }

  function quitarTarea() {
    setModelo(m => (m.tareas.length <= 1 ? m : {
      ...m,
      tareas: m.tareas.slice(0, -1),
      matrizCostos: m.matrizCostos.map(fila => fila.slice(0, -1)),
    }))
  }

  /** Reemplaza el modelo completo (p.ej. cuando el chat propone un problema de redes). */
  function reemplazar(nuevo: ModeloRed) {
    setModelo(m => desdeModeloRed(nuevo, m))
  }

  return {
    modelo,
    setMetodo, setDirigido, setFuente, setSumidero,
    agregarNodo, quitarNodo, renombrarNodo,
    agregarArista, quitarArista, setArista,
    setCosto, setNombreAgente, setNombreTarea,
    agregarAgente, quitarAgente, agregarTarea, quitarTarea,
    reemplazar,
  }
}
