import { useState } from 'react'
import type {
  ModeloEntero, TipoVariable, TipoObjetivo, TipoRestriccion,
} from '@/types/io'

/**
 * Estado del formulario de PL Entera: la relajación LP (variables/objetivo/
 * restricciones) más un `tiposVariable` alineado por índice con las variables.
 * Espeja useModeloForm pero añade el tipo de integralidad por variable.
 */
export interface EnteraFormModelo {
  variables: string[]
  objetivo: { coeficientes: number[]; tipo: TipoObjetivo }
  restricciones: { coeficientes: number[]; tipo: TipoRestriccion; rhs: number }[]
  tiposVariable: TipoVariable[]
}

// Ejemplo clásico del API_CONTRACT: MAX 5x1+4x2, óptimo entero 20 en (4,0).
const MODELO_EJEMPLO: EnteraFormModelo = {
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [5, 4], tipo: 'MAXIMIZAR' },
  restricciones: [
    { coeficientes: [6, 4], tipo: 'LEQ', rhs: 24 },
    { coeficientes: [1, 2], tipo: 'LEQ', rhs: 6 },
  ],
  tiposVariable: ['ENTERA', 'ENTERA'],
}

/** Convierte el estado del formulario al `ModeloEntero` que espera el backend. */
export function aModeloEntero(f: EnteraFormModelo): ModeloEntero {
  return {
    relajacion: {
      variables: f.variables,
      objetivo: { coeficientes: f.objetivo.coeficientes, tipo: f.objetivo.tipo },
      restricciones: f.restricciones.map(r => ({
        coeficientes: [...r.coeficientes],
        tipo: r.tipo,
        rhs: r.rhs,
      })),
    },
    tiposVariable: [...f.tiposVariable],
  }
}

/** Rellena el formulario desde un `ModeloEntero` (p.ej. el que propone el chat). */
export function desdeModeloEntero(m: ModeloEntero): EnteraFormModelo {
  const rel = m.relajacion
  return {
    variables: [...rel.variables],
    objetivo: { coeficientes: [...rel.objetivo.coeficientes], tipo: rel.objetivo.tipo },
    restricciones: rel.restricciones.map(r => ({
      coeficientes: [...r.coeficientes],
      tipo: r.tipo,
      rhs: r.rhs,
    })),
    // Si el modelo no trae tipos alineados, asume enteras.
    tiposVariable: rel.variables.map((_, i) => m.tiposVariable[i] ?? 'ENTERA'),
  }
}

/** Estado editable de un modelo de PL Entera para el formulario. */
export function useEnteraForm(inicial: EnteraFormModelo = MODELO_EJEMPLO) {
  const [modelo, setModelo] = useState<EnteraFormModelo>(inicial)

  function agregarVariable() {
    setModelo(m => {
      const n = m.variables.length + 1
      return {
        ...m,
        variables: [...m.variables, `x${n}`],
        objetivo: { ...m.objetivo, coeficientes: [...m.objetivo.coeficientes, 0] },
        restricciones: m.restricciones.map(r => ({ ...r, coeficientes: [...r.coeficientes, 0] })),
        tiposVariable: [...m.tiposVariable, 'ENTERA'],
      }
    })
  }

  function quitarVariable(index: number) {
    setModelo(m => {
      if (m.variables.length <= 1) return m
      return {
        ...m,
        variables: m.variables.filter((_, i) => i !== index),
        objetivo: { ...m.objetivo, coeficientes: m.objetivo.coeficientes.filter((_, i) => i !== index) },
        restricciones: m.restricciones.map(r => ({ ...r, coeficientes: r.coeficientes.filter((_, i) => i !== index) })),
        tiposVariable: m.tiposVariable.filter((_, i) => i !== index),
      }
    })
  }

  function agregarRestriccion() {
    setModelo(m => ({
      ...m,
      restricciones: [
        ...m.restricciones,
        { coeficientes: m.variables.map(() => 0), tipo: 'LEQ' as TipoRestriccion, rhs: 0 },
      ],
    }))
  }

  function quitarRestriccion(index: number) {
    setModelo(m => (m.restricciones.length <= 1 ? m : {
      ...m,
      restricciones: m.restricciones.filter((_, i) => i !== index),
    }))
  }

  function setCoeficienteObjetivo(index: number, valor: number) {
    setModelo(m => {
      const coeficientes = [...m.objetivo.coeficientes]
      coeficientes[index] = valor
      return { ...m, objetivo: { ...m.objetivo, coeficientes } }
    })
  }

  function setTipoObjetivo(tipo: TipoObjetivo) {
    setModelo(m => ({ ...m, objetivo: { ...m.objetivo, tipo } }))
  }

  function setCoeficienteRestriccion(fila: number, col: number, valor: number) {
    setModelo(m => ({
      ...m,
      restricciones: m.restricciones.map((r, i) => {
        if (i !== fila) return r
        const coeficientes = [...r.coeficientes]
        coeficientes[col] = valor
        return { ...r, coeficientes }
      }),
    }))
  }

  function setRhs(index: number, valor: number) {
    setModelo(m => ({
      ...m,
      restricciones: m.restricciones.map((r, i) => (i === index ? { ...r, rhs: valor } : r)),
    }))
  }

  function setTipoRestriccion(index: number, tipo: TipoRestriccion) {
    setModelo(m => ({
      ...m,
      restricciones: m.restricciones.map((r, i) => (i === index ? { ...r, tipo } : r)),
    }))
  }

  function setNombreVariable(index: number, nombre: string) {
    setModelo(m => {
      const variables = [...m.variables]
      variables[index] = nombre
      return { ...m, variables }
    })
  }

  function setTipoVariable(index: number, tipo: TipoVariable) {
    setModelo(m => {
      const tiposVariable = [...m.tiposVariable]
      tiposVariable[index] = tipo
      return { ...m, tiposVariable }
    })
  }

  /** Reemplaza el modelo completo (p.ej. cuando el chat propone un problema entero). */
  function reemplazar(nuevo: ModeloEntero) {
    setModelo(desdeModeloEntero(nuevo))
  }

  return {
    modelo,
    agregarVariable, quitarVariable,
    agregarRestriccion, quitarRestriccion,
    setCoeficienteObjetivo, setTipoObjetivo,
    setCoeficienteRestriccion, setRhs, setTipoRestriccion,
    setNombreVariable, setTipoVariable,
    reemplazar,
  }
}
