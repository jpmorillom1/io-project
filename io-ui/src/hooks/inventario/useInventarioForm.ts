import { useState } from 'react'
import type { ModeloInventario, MetodoInventario, TramoDescuento } from '@/types/io'

/**
 * Estado del formulario de Inventarios: mantiene TODOS los parámetros poblados
 * para poder cambiar de submodelo sin perder lo tecleado. Al enviar,
 * `aModeloInventario` recorta el body a los campos que aplican al submodelo.
 */
export interface InventarioFormModelo {
  metodo: MetodoInventario
  demanda: number
  costoOrden: number
  costoMantener: number
  costoFaltante: number
  tasaProduccion: number
  leadTimeDias: number
  diasHabiles: number
  tasaMantenerPorcentaje: number
  tramos: TramoDescuento[]
}

// Valores del API_CONTRACT (EOQ básico D=1000, K=50, H=4 → Q*≈158).
const MODELO_EJEMPLO: InventarioFormModelo = {
  metodo: 'EOQ_BASICO',
  demanda: 1000,
  costoOrden: 50,
  costoMantener: 4,
  costoFaltante: 10,
  tasaProduccion: 2000,
  leadTimeDias: 10,
  diasHabiles: 360,
  tasaMantenerPorcentaje: 0.2,
  tramos: [
    { cantidadMinima: 0, precioUnitario: 5.0 },
    { cantidadMinima: 1000, precioUnitario: 4.8 },
    { cantidadMinima: 2500, precioUnitario: 4.75 },
  ],
}

/** Convierte el estado del formulario al `ModeloInventario` que espera el backend. */
export function aModeloInventario(f: InventarioFormModelo): ModeloInventario {
  const base = { metodo: f.metodo, demanda: f.demanda, costoOrden: f.costoOrden }
  switch (f.metodo) {
    case 'EOQ_BASICO':
      return { ...base, costoMantener: f.costoMantener }
    case 'PRODUCCION_ECONOMICA':
      return { ...base, costoMantener: f.costoMantener, tasaProduccion: f.tasaProduccion }
    case 'EOQ_FALTANTES':
      return { ...base, costoMantener: f.costoMantener, costoFaltante: f.costoFaltante }
    case 'PUNTO_REORDEN':
      return { ...base, costoMantener: f.costoMantener, leadTimeDias: f.leadTimeDias, diasHabiles: f.diasHabiles }
    case 'EOQ_DESCUENTOS':
      return { ...base, tasaMantenerPorcentaje: f.tasaMantenerPorcentaje, tramos: f.tramos.map(t => ({ ...t })) }
  }
}

/** Rellena el formulario desde un `ModeloInventario` (p.ej. el que propone el chat). */
export function desdeModeloInventario(
  m: ModeloInventario,
  base: InventarioFormModelo = MODELO_EJEMPLO
): InventarioFormModelo {
  return {
    metodo: m.metodo ?? base.metodo,
    demanda: m.demanda ?? base.demanda,
    costoOrden: m.costoOrden ?? base.costoOrden,
    costoMantener: m.costoMantener ?? base.costoMantener,
    costoFaltante: m.costoFaltante ?? base.costoFaltante,
    tasaProduccion: m.tasaProduccion ?? base.tasaProduccion,
    leadTimeDias: m.leadTimeDias ?? base.leadTimeDias,
    diasHabiles: m.diasHabiles ?? base.diasHabiles,
    tasaMantenerPorcentaje: m.tasaMantenerPorcentaje ?? base.tasaMantenerPorcentaje,
    tramos: (m.tramos ?? base.tramos).map(t => ({ ...t })),
  }
}

/** Estado editable de un modelo de inventario para el formulario. */
export function useInventarioForm(inicial: InventarioFormModelo = MODELO_EJEMPLO) {
  const [modelo, setModelo] = useState<InventarioFormModelo>(inicial)

  function setMetodo(metodo: MetodoInventario) {
    setModelo(m => ({ ...m, metodo }))
  }

  function setCampo<K extends keyof InventarioFormModelo>(campo: K, valor: InventarioFormModelo[K]) {
    setModelo(m => ({ ...m, [campo]: valor }))
  }

  function setTramo(idx: number, cambios: Partial<TramoDescuento>) {
    setModelo(m => {
      const tramos = m.tramos.map((t, i) => (i === idx ? { ...t, ...cambios } : t))
      return { ...m, tramos }
    })
  }

  function agregarTramo() {
    setModelo(m => {
      const ultimo = m.tramos[m.tramos.length - 1]
      return {
        ...m,
        tramos: [...m.tramos, {
          cantidadMinima: ultimo ? ultimo.cantidadMinima + 1000 : 0,
          precioUnitario: ultimo ? ultimo.precioUnitario : 0,
        }],
      }
    })
  }

  function quitarTramo(idx: number) {
    setModelo(m => (m.tramos.length <= 1 ? m : { ...m, tramos: m.tramos.filter((_, i) => i !== idx) }))
  }

  /** Reemplaza el modelo completo (p.ej. cuando el chat propone un problema de inventario). */
  function reemplazar(nuevo: ModeloInventario) {
    setModelo(m => desdeModeloInventario(nuevo, m))
  }

  return { modelo, setMetodo, setCampo, setTramo, agregarTramo, quitarTramo, reemplazar }
}
