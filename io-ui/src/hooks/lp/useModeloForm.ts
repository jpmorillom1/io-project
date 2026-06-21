import { useWorkspaceStore } from '@/store/useWorkspaceStore'
import type { ModeloLP, TipoObjetivo, TipoRestriccion } from '@/types/io'

const modeloVacio = (): ModeloLP => ({
  variables: ['x1', 'x2'],
  objetivo: { coeficientes: [0, 0], tipo: 'MAXIMIZAR' },
  restricciones: [
    { coeficientes: [0, 0], tipo: 'LEQ', rhs: 0 },
  ],
})

export function useModeloForm() {
  const modelo = useWorkspaceStore(s => s.modelo)
  const status = useWorkspaceStore(s => s.status)
  const setModelo = useWorkspaceStore(s => s.setModelo)
  const resetResultado = useWorkspaceStore(s => s.resetResultado)

  const current = modelo ?? modeloVacio()

  function touch(next: ModeloLP) {
    if (status === 'SOLVED') {
      resetResultado()
    } else {
      useWorkspaceStore.getState().setStatus('EDITING')
    }
    setModelo(next)
  }

  function agregarVariable() {
    const n = current.variables.length + 1
    touch({
      ...current,
      variables: [...current.variables, `x${n}`],
      objetivo: { ...current.objetivo, coeficientes: [...current.objetivo.coeficientes, 0] },
      restricciones: current.restricciones.map(r => ({
        ...r,
        coeficientes: [...r.coeficientes, 0],
      })),
    })
  }

  function quitarVariable(index: number) {
    if (current.variables.length <= 1) return
    touch({
      ...current,
      variables: current.variables.filter((_, i) => i !== index),
      objetivo: {
        ...current.objetivo,
        coeficientes: current.objetivo.coeficientes.filter((_, i) => i !== index),
      },
      restricciones: current.restricciones.map(r => ({
        ...r,
        coeficientes: r.coeficientes.filter((_, i) => i !== index),
      })),
    })
  }

  function agregarRestriccion() {
    touch({
      ...current,
      restricciones: [
        ...current.restricciones,
        { coeficientes: current.variables.map(() => 0), tipo: 'LEQ' as TipoRestriccion, rhs: 0 },
      ],
    })
  }

  function quitarRestriccion(index: number) {
    if (current.restricciones.length <= 1) return
    touch({
      ...current,
      restricciones: current.restricciones.filter((_, i) => i !== index),
    })
  }

  function setCoeficienteObjetivo(index: number, valor: number) {
    const coefs = [...current.objetivo.coeficientes]
    coefs[index] = valor
    touch({ ...current, objetivo: { ...current.objetivo, coeficientes: coefs } })
  }

  function setTipoObjetivo(tipo: TipoObjetivo) {
    touch({ ...current, objetivo: { ...current.objetivo, tipo } })
  }

  function setCoeficienteRestriccion(fila: number, col: number, valor: number) {
    const restricciones = current.restricciones.map((r, i) => {
      if (i !== fila) return r
      const coefs = [...r.coeficientes]
      coefs[col] = valor
      return { ...r, coeficientes: coefs }
    })
    touch({ ...current, restricciones })
  }

  function setRhs(index: number, valor: number) {
    touch({
      ...current,
      restricciones: current.restricciones.map((r, i) =>
        i === index ? { ...r, rhs: valor } : r
      ),
    })
  }

  function setTipoRestriccion(index: number, tipo: TipoRestriccion) {
    touch({
      ...current,
      restricciones: current.restricciones.map((r, i) =>
        i === index ? { ...r, tipo } : r
      ),
    })
  }

  function setNombreVariable(index: number, nombre: string) {
    const variables = [...current.variables]
    variables[index] = nombre
    touch({ ...current, variables })
  }

  return {
    modelo: current,
    agregarVariable,
    quitarVariable,
    agregarRestriccion,
    quitarRestriccion,
    setCoeficienteObjetivo,
    setTipoObjetivo,
    setCoeficienteRestriccion,
    setRhs,
    setTipoRestriccion,
    setNombreVariable,
  }
}
