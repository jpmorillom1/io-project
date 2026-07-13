import { useState } from 'react'
import type {
  ModeloDinamico, MetodoDinamico, SentidoOptimizacion,
  ActividadRecurso, ArticuloMochila, EtapaRuta, ArcoRuta, DatosEdadEquipo,
} from '@/types/io'

/**
 * Estado del formulario de Programación Dinámica. Los 5 submodelos tienen entradas
 * disjuntas: se mantienen TODAS pobladas para poder cambiar de submodelo sin perder
 * lo tecleado. Al enviar, `aModeloDinamico` recorta el body a los campos del método.
 */
export interface DinamicaFormModelo {
  metodo: MetodoDinamico
  sentido: SentidoOptimizacion
  // Asignación de recursos
  recursoTotal: number
  actividades: ActividadRecurso[]
  // Mochila (unidadesMaximas undefined ⇒ 0/1)
  capacidad: number
  articulos: ArticuloMochila[]
  // Ruta por etapas
  etapasRuta: EtapaRuta[]
  arcos: ArcoRuta[]
  // Planificación de producción
  demandas: number[]
  costoPreparacion: number
  costoUnitarioProduccion: number
  costoMantener: number
  capacidadProduccion: number
  capacidadAlmacen: number
  inventarioInicial: number
  inventarioFinal: number
  // Reemplazo de equipos
  horizonteAnios: number
  edadInicial: number
  edadMaxima: number
  costoCompra: number
  tablaEdades: DatosEdadEquipo[]
}

const MODELO_EJEMPLO: DinamicaFormModelo = {
  metodo: 'ASIGNACION_RECURSOS',
  sentido: 'MAXIMIZAR',
  recursoTotal: 2,
  actividades: [
    { nombre: 'A', retornos: [0, 4, 6] },
    { nombre: 'B', retornos: [0, 3, 8] },
  ],
  capacidad: 5,
  articulos: [
    { nombre: 'A', peso: 2, valor: 3 },
    { nombre: 'B', peso: 3, valor: 4 },
    { nombre: 'C', peso: 4, valor: 5 },
  ],
  etapasRuta: [
    { etapa: 1, nodos: ['A'] },
    { etapa: 2, nodos: ['B', 'C'] },
    { etapa: 3, nodos: ['D', 'E'] },
    { etapa: 4, nodos: ['F'] },
  ],
  arcos: [
    { origen: 'A', destino: 'B', costo: 4 },
    { origen: 'A', destino: 'C', costo: 2 },
    { origen: 'B', destino: 'D', costo: 3 },
    { origen: 'B', destino: 'E', costo: 6 },
    { origen: 'C', destino: 'D', costo: 5 },
    { origen: 'C', destino: 'E', costo: 4 },
    { origen: 'D', destino: 'F', costo: 7 },
    { origen: 'E', destino: 'F', costo: 3 },
  ],
  demandas: [3, 2, 4],
  costoPreparacion: 3,
  costoUnitarioProduccion: 1,
  costoMantener: 1,
  capacidadProduccion: 0,
  capacidadAlmacen: 0,
  inventarioInicial: 0,
  inventarioFinal: 0,
  horizonteAnios: 2,
  edadInicial: 0,
  edadMaxima: 2,
  costoCompra: 10,
  tablaEdades: [
    { edad: 0, ingreso: 20, costoOperacion: 2, valorRescate: 8 },
    { edad: 1, ingreso: 18, costoOperacion: 4, valorRescate: 6 },
    { edad: 2, ingreso: 15, costoOperacion: 8, valorRescate: 3 },
  ],
}

// El sentido solo es configurable (y debe enviarse) en estos dos submodelos.
const SENTIDO_CONFIGURABLE: Record<MetodoDinamico, boolean> = {
  ASIGNACION_RECURSOS: true,
  RUTA_ETAPAS: true,
  MOCHILA: false,
  PLANIFICACION_PRODUCCION: false,
  REEMPLAZO_EQUIPOS: false,
}

export function sentidoConfigurable(metodo: MetodoDinamico): boolean {
  return SENTIDO_CONFIGURABLE[metodo]
}

/** Convierte el estado del formulario al `ModeloDinamico` que espera el backend. */
export function aModeloDinamico(f: DinamicaFormModelo): ModeloDinamico {
  switch (f.metodo) {
    case 'ASIGNACION_RECURSOS':
      return {
        metodo: f.metodo,
        sentido: f.sentido,
        recursoTotal: f.recursoTotal,
        actividades: f.actividades.map(a => ({ nombre: a.nombre, retornos: [...a.retornos] })),
      }
    case 'MOCHILA':
      return {
        metodo: f.metodo,
        capacidad: f.capacidad,
        articulos: f.articulos.map(a => {
          const base: ArticuloMochila = { nombre: a.nombre, peso: a.peso, valor: a.valor }
          // unidadesMaximas se OMITE (no null) para mochila 0/1.
          if (a.unidadesMaximas != null) base.unidadesMaximas = a.unidadesMaximas
          return base
        }),
      }
    case 'RUTA_ETAPAS':
      return {
        metodo: f.metodo,
        sentido: f.sentido,
        etapasRuta: f.etapasRuta.map(e => ({ etapa: e.etapa, nodos: [...e.nodos] })),
        arcos: f.arcos.map(a => ({ ...a })),
      }
    case 'PLANIFICACION_PRODUCCION': {
      const m: ModeloDinamico = {
        metodo: f.metodo,
        demandas: [...f.demandas],
        costoPreparacion: f.costoPreparacion,
        costoUnitarioProduccion: f.costoUnitarioProduccion,
        costoMantener: f.costoMantener,
      }
      // Campos avanzados opcionales: se omiten si valen 0 (= "sin límite" / default).
      if (f.capacidadProduccion > 0) m.capacidadProduccion = f.capacidadProduccion
      if (f.capacidadAlmacen > 0) m.capacidadAlmacen = f.capacidadAlmacen
      if (f.inventarioInicial > 0) m.inventarioInicial = f.inventarioInicial
      if (f.inventarioFinal > 0) m.inventarioFinal = f.inventarioFinal
      return m
    }
    case 'REEMPLAZO_EQUIPOS':
      return {
        metodo: f.metodo,
        horizonteAnios: f.horizonteAnios,
        edadInicial: f.edadInicial,
        edadMaxima: f.edadMaxima,
        costoCompra: f.costoCompra,
        tablaEdades: f.tablaEdades.map(e => ({ ...e })),
      }
  }
}

/** Rellena el formulario desde un `ModeloDinamico` (p.ej. el que propone el chat). */
export function desdeModeloDinamico(
  m: ModeloDinamico,
  base: DinamicaFormModelo = MODELO_EJEMPLO
): DinamicaFormModelo {
  return {
    metodo: m.metodo ?? base.metodo,
    sentido: m.sentido ?? base.sentido,
    recursoTotal: m.recursoTotal ?? base.recursoTotal,
    actividades: (m.actividades ?? base.actividades).map(a => ({ nombre: a.nombre, retornos: [...a.retornos] })),
    capacidad: m.capacidad ?? base.capacidad,
    articulos: (m.articulos ?? base.articulos).map(a => ({ ...a })),
    etapasRuta: (m.etapasRuta ?? base.etapasRuta).map(e => ({ etapa: e.etapa, nodos: [...e.nodos] })),
    arcos: (m.arcos ?? base.arcos).map(a => ({ ...a })),
    demandas: m.demandas ? [...m.demandas] : [...base.demandas],
    costoPreparacion: m.costoPreparacion ?? base.costoPreparacion,
    costoUnitarioProduccion: m.costoUnitarioProduccion ?? base.costoUnitarioProduccion,
    costoMantener: m.costoMantener ?? base.costoMantener,
    capacidadProduccion: m.capacidadProduccion ?? 0,
    capacidadAlmacen: m.capacidadAlmacen ?? 0,
    inventarioInicial: m.inventarioInicial ?? 0,
    inventarioFinal: m.inventarioFinal ?? 0,
    horizonteAnios: m.horizonteAnios ?? base.horizonteAnios,
    edadInicial: m.edadInicial ?? base.edadInicial,
    edadMaxima: m.edadMaxima ?? base.edadMaxima,
    costoCompra: m.costoCompra ?? base.costoCompra,
    tablaEdades: (m.tablaEdades ?? base.tablaEdades).map(e => ({ ...e })),
  }
}

/** Todos los nombres de nodo declarados en las etapas de ruta (para los selects de arcos). */
export function nodosDeRuta(f: DinamicaFormModelo): string[] {
  return f.etapasRuta.flatMap(e => e.nodos)
}

export function useDinamicaForm(inicial: DinamicaFormModelo = MODELO_EJEMPLO) {
  const [modelo, setModelo] = useState<DinamicaFormModelo>(inicial)

  function setMetodo(metodo: MetodoDinamico) {
    setModelo(m => ({
      ...m,
      metodo,
      // Ajusta el sentido al default natural del submodelo si no es configurable.
      sentido: metodo === 'RUTA_ETAPAS' ? 'MINIMIZAR' : metodo === 'ASIGNACION_RECURSOS' ? 'MAXIMIZAR' : m.sentido,
    }))
  }

  function setCampo<K extends keyof DinamicaFormModelo>(campo: K, valor: DinamicaFormModelo[K]) {
    setModelo(m => ({ ...m, [campo]: valor }))
  }

  // ── Asignación de recursos ─────────────────────────────────────────────────
  function setRecursoTotal(valor: number) {
    const n = Math.max(1, Math.round(valor))
    setModelo(m => ({
      ...m,
      recursoTotal: n,
      // Redimensiona los retornos de cada actividad a n+1 (rellena con 0, recorta si sobra).
      actividades: m.actividades.map(a => ({
        ...a,
        retornos: Array.from({ length: n + 1 }, (_, i) => a.retornos[i] ?? 0),
      })),
    }))
  }
  function setNombreActividad(i: number, nombre: string) {
    setModelo(m => ({ ...m, actividades: m.actividades.map((a, idx) => (idx === i ? { ...a, nombre } : a)) }))
  }
  function setRetorno(i: number, k: number, valor: number) {
    setModelo(m => ({
      ...m,
      actividades: m.actividades.map((a, idx) => {
        if (idx !== i) return a
        const retornos = [...a.retornos]
        retornos[k] = valor
        return { ...a, retornos }
      }),
    }))
  }
  function agregarActividad() {
    setModelo(m => ({
      ...m,
      actividades: [...m.actividades, { nombre: `Act${m.actividades.length + 1}`, retornos: Array(m.recursoTotal + 1).fill(0) }],
    }))
  }
  function quitarActividad(i: number) {
    setModelo(m => (m.actividades.length <= 1 ? m : { ...m, actividades: m.actividades.filter((_, idx) => idx !== i) }))
  }

  // ── Mochila ────────────────────────────────────────────────────────────────
  function setArticulo(i: number, cambios: Partial<ArticuloMochila>) {
    setModelo(m => ({ ...m, articulos: m.articulos.map((a, idx) => (idx === i ? { ...a, ...cambios } : a)) }))
  }
  function toggleUnidades(i: number) {
    setModelo(m => ({
      ...m,
      articulos: m.articulos.map((a, idx) =>
        idx === i ? { ...a, unidadesMaximas: a.unidadesMaximas == null ? 2 : undefined } : a
      ),
    }))
  }
  function agregarArticulo() {
    setModelo(m => ({ ...m, articulos: [...m.articulos, { nombre: `Art${m.articulos.length + 1}`, peso: 1, valor: 1 }] }))
  }
  function quitarArticulo(i: number) {
    setModelo(m => (m.articulos.length <= 1 ? m : { ...m, articulos: m.articulos.filter((_, idx) => idx !== i) }))
  }

  // ── Ruta por etapas ────────────────────────────────────────────────────────
  function setNodo(etapaIdx: number, nodoIdx: number, nombre: string) {
    setModelo(m => {
      const viejo = m.etapasRuta[etapaIdx]?.nodos[nodoIdx]
      const etapasRuta = m.etapasRuta.map((e, idx) => {
        if (idx !== etapaIdx) return e
        const nodos = [...e.nodos]
        nodos[nodoIdx] = nombre
        return { ...e, nodos }
      })
      // Propaga el rename a los arcos.
      const arcos = m.arcos.map(a => ({
        ...a,
        origen: a.origen === viejo ? nombre : a.origen,
        destino: a.destino === viejo ? nombre : a.destino,
      }))
      return { ...m, etapasRuta, arcos }
    })
  }
  function agregarNodoEtapa(etapaIdx: number) {
    setModelo(m => ({
      ...m,
      etapasRuta: m.etapasRuta.map((e, idx) => (idx === etapaIdx ? { ...e, nodos: [...e.nodos, `N${nodosDeRuta(m).length + 1}`] } : e)),
    }))
  }
  function quitarNodoEtapa(etapaIdx: number, nodoIdx: number) {
    setModelo(m => ({
      ...m,
      etapasRuta: m.etapasRuta.map((e, idx) =>
        idx === etapaIdx && e.nodos.length > 1 ? { ...e, nodos: e.nodos.filter((_, j) => j !== nodoIdx) } : e
      ),
    }))
  }
  function agregarEtapa() {
    setModelo(m => ({
      ...m,
      etapasRuta: [...m.etapasRuta, { etapa: m.etapasRuta.length + 1, nodos: [`N${nodosDeRuta(m).length + 1}`] }],
    }))
  }
  function quitarEtapa() {
    setModelo(m => (m.etapasRuta.length <= 2 ? m : { ...m, etapasRuta: m.etapasRuta.slice(0, -1) }))
  }
  function setArco(i: number, cambios: Partial<ArcoRuta>) {
    setModelo(m => ({ ...m, arcos: m.arcos.map((a, idx) => (idx === i ? { ...a, ...cambios } : a)) }))
  }
  function agregarArco() {
    setModelo(m => {
      const nodos = nodosDeRuta(m)
      return { ...m, arcos: [...m.arcos, { origen: nodos[0] ?? '', destino: nodos[1] ?? '', costo: 0 }] }
    })
  }
  function quitarArco(i: number) {
    setModelo(m => ({ ...m, arcos: m.arcos.filter((_, idx) => idx !== i) }))
  }

  // ── Planificación de producción ────────────────────────────────────────────
  function setDemanda(i: number, valor: number) {
    setModelo(m => {
      const demandas = [...m.demandas]
      demandas[i] = valor
      return { ...m, demandas }
    })
  }
  function agregarPeriodo() {
    setModelo(m => ({ ...m, demandas: [...m.demandas, 0] }))
  }
  function quitarPeriodo() {
    setModelo(m => (m.demandas.length <= 1 ? m : { ...m, demandas: m.demandas.slice(0, -1) }))
  }

  // ── Reemplazo de equipos ───────────────────────────────────────────────────
  function setEdadMaxima(valor: number) {
    const n = Math.max(0, Math.round(valor))
    setModelo(m => ({
      ...m,
      edadMaxima: n,
      // Regenera la tabla de edades 0..n (conserva lo tecleado, rellena huecos).
      tablaEdades: Array.from({ length: n + 1 }, (_, edad) =>
        m.tablaEdades.find(e => e.edad === edad) ?? { edad, ingreso: 0, costoOperacion: 0, valorRescate: 0 }
      ),
      edadInicial: Math.min(m.edadInicial, n),
    }))
  }
  function setEdadDato(edad: number, cambios: Partial<DatosEdadEquipo>) {
    setModelo(m => ({ ...m, tablaEdades: m.tablaEdades.map(e => (e.edad === edad ? { ...e, ...cambios } : e)) }))
  }

  /** Reemplaza el modelo completo (p.ej. cuando el chat propone un problema de PD). */
  function reemplazar(nuevo: ModeloDinamico) {
    setModelo(m => desdeModeloDinamico(nuevo, m))
  }

  return {
    modelo,
    setMetodo, setCampo,
    setRecursoTotal, setNombreActividad, setRetorno, agregarActividad, quitarActividad,
    setArticulo, toggleUnidades, agregarArticulo, quitarArticulo,
    setNodo, agregarNodoEtapa, quitarNodoEtapa, agregarEtapa, quitarEtapa,
    setArco, agregarArco, quitarArco,
    setDemanda, agregarPeriodo, quitarPeriodo,
    setEdadMaxima, setEdadDato,
    reemplazar,
  }
}
