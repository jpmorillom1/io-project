import { useState, useMemo } from 'react'
import type { ModeloTransporte, MetodoTransporte } from '@/types/io'

const MODELO_EJEMPLO: ModeloTransporte = {
  origenes: ['O1', 'O2', 'O3'],
  destinos: ['D1', 'D2', 'D3'],
  oferta: [20, 30, 25],
  demanda: [30, 25, 20],
  costos: [
    [4, 6, 8],
    [6, 4, 2],
    [2, 8, 6],
  ],
  metodo: 'MODI',
}

/** Estado editable de un modelo de transporte para el formulario de matriz. */
export function useTransporteForm(inicial: ModeloTransporte = MODELO_EJEMPLO) {
  const [modelo, setModelo] = useState<ModeloTransporte>(inicial)

  const totales = useMemo(() => {
    const sumaOferta = modelo.oferta.reduce((a, b) => a + b, 0)
    const sumaDemanda = modelo.demanda.reduce((a, b) => a + b, 0)
    return { sumaOferta, sumaDemanda, balanceado: Math.abs(sumaOferta - sumaDemanda) < 1e-9 }
  }, [modelo])

  function setCelda(i: number, j: number, valor: number) {
    setModelo(m => {
      const costos = m.costos.map(fila => [...fila])
      costos[i][j] = valor
      return { ...m, costos }
    })
  }

  function setOferta(i: number, valor: number) {
    setModelo(m => {
      const oferta = [...m.oferta]
      oferta[i] = valor
      return { ...m, oferta }
    })
  }

  function setDemanda(j: number, valor: number) {
    setModelo(m => {
      const demanda = [...m.demanda]
      demanda[j] = valor
      return { ...m, demanda }
    })
  }

  function setNombreOrigen(i: number, nombre: string) {
    setModelo(m => {
      const origenes = [...m.origenes]
      origenes[i] = nombre
      return { ...m, origenes }
    })
  }

  function setNombreDestino(j: number, nombre: string) {
    setModelo(m => {
      const destinos = [...m.destinos]
      destinos[j] = nombre
      return { ...m, destinos }
    })
  }

  function agregarOrigen() {
    setModelo(m => ({
      ...m,
      origenes: [...m.origenes, `O${m.origenes.length + 1}`],
      oferta: [...m.oferta, 0],
      costos: [...m.costos, m.destinos.map(() => 0)],
    }))
  }

  function quitarOrigen() {
    setModelo(m => (m.origenes.length <= 1 ? m : {
      ...m,
      origenes: m.origenes.slice(0, -1),
      oferta: m.oferta.slice(0, -1),
      costos: m.costos.slice(0, -1),
    }))
  }

  function agregarDestino() {
    setModelo(m => ({
      ...m,
      destinos: [...m.destinos, `D${m.destinos.length + 1}`],
      demanda: [...m.demanda, 0],
      costos: m.costos.map(fila => [...fila, 0]),
    }))
  }

  function quitarDestino() {
    setModelo(m => (m.destinos.length <= 1 ? m : {
      ...m,
      destinos: m.destinos.slice(0, -1),
      demanda: m.demanda.slice(0, -1),
      costos: m.costos.map(fila => fila.slice(0, -1)),
    }))
  }

  function setMetodo(metodo: MetodoTransporte) {
    setModelo(m => ({ ...m, metodo }))
  }

  /** Reemplaza el modelo completo (p.ej. cuando el chat propone un problema de transporte). */
  function reemplazar(nuevo: ModeloTransporte) {
    setModelo(nuevo)
  }

  return {
    modelo, totales,
    setCelda, setOferta, setDemanda, setNombreOrigen, setNombreDestino,
    agregarOrigen, quitarOrigen, agregarDestino, quitarDestino, setMetodo, reemplazar,
  }
}
