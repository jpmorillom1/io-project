import { create } from 'zustand'
import type {
  ModeloLP, SolveResult, SolveResultGrafico, WorkspaceStatus, ValidacionResponse,
  ModeloTransporte, SolveResultTransporte, ModeloRed, SolveResultRed,
  ModeloEntero, SolveResultEntera,
  ModeloInventario, SolveResultInventario,
  ModeloDinamico, SolveResultDinamica,
} from '@/types/io'

type ActualizacionIA = 'modelo' | 'validacion' | 'resultado' | null

interface WorkspaceStore {
  status: WorkspaceStatus
  sesionId: string | null
  descripcionProblema: string
  modelo: ModeloLP | null
  erroresValidacion: string[]
  sugerenciasValidacion: string[]
  resultado: SolveResult | null
  resultadoGrafico: SolveResultGrafico | null
  modeloTransporte: ModeloTransporte | null
  modeloTransporteGrafico: ModeloTransporte | null
  resultadoTransporte: SolveResultTransporte | null
  modeloRed: ModeloRed | null
  modeloRedGrafico: ModeloRed | null
  resultadoRed: SolveResultRed | null
  modeloEntero: ModeloEntero | null
  resultadoEntero: SolveResultEntera | null
  modeloInventario: ModeloInventario | null
  resultadoInventario: SolveResultInventario | null
  modeloDinamico: ModeloDinamico | null
  resultadoDinamica: SolveResultDinamica | null
  validado: boolean
  isChatBusy: boolean
  ultimaActualizacionIA: ActualizacionIA
  /**
   * Se incrementa cada vez que la IA entrega un modelo, una validación o un resultado.
   * Los workspaces lo usan como `key` del grupo de cards para re-animar su entrada.
   * No cambia al editar a mano, así que escribir en el formulario no relanza nada.
   */
  revisionIA: number

  setStatus: (s: WorkspaceStatus) => void
  setSesionId: (id: string | null) => void
  setDescripcion: (d: string) => void
  setModelo: (m: ModeloLP | null) => void
  setErroresValidacion: (e: string[]) => void
  setResultado: (r: SolveResult | null) => void
  setResultadoGrafico: (r: SolveResultGrafico | null) => void
  setModeloTransporte: (m: ModeloTransporte | null) => void
  setModeloTransporteGrafico: (m: ModeloTransporte | null) => void
  setResultadoTransporte: (r: SolveResultTransporte | null) => void
  setModeloRed: (m: ModeloRed | null) => void
  setModeloRedGrafico: (m: ModeloRed | null) => void
  setResultadoRed: (r: SolveResultRed | null) => void
  setModeloEntero: (m: ModeloEntero | null) => void
  setResultadoEntero: (r: SolveResultEntera | null) => void
  setModeloInventario: (m: ModeloInventario | null) => void
  setResultadoInventario: (r: SolveResultInventario | null) => void
  setModeloDinamico: (m: ModeloDinamico | null) => void
  setResultadoDinamica: (r: SolveResultDinamica | null) => void
  setValidado: (v: boolean) => void
  resetResultado: () => void
  setValidacion: (v: ValidacionResponse) => void
  setIsChatBusy: (b: boolean) => void
  setUltimaActualizacionIA: (tipo: ActualizacionIA) => void
}

export const useWorkspaceStore = create<WorkspaceStore>((set) => ({
  status: 'IDLE',
  sesionId: null,
  descripcionProblema: '',
  modelo: null,
  erroresValidacion: [],
  sugerenciasValidacion: [],
  resultado: null,
  resultadoGrafico: null,
  modeloTransporte: null,
  modeloTransporteGrafico: null,
  resultadoTransporte: null,
  modeloRed: null,
  modeloRedGrafico: null,
  resultadoRed: null,
  modeloEntero: null,
  resultadoEntero: null,
  modeloInventario: null,
  resultadoInventario: null,
  modeloDinamico: null,
  resultadoDinamica: null,
  validado: false,
  isChatBusy: false,
  ultimaActualizacionIA: null,
  revisionIA: 0,

  setStatus: (status) => set({ status }),
  setSesionId: (sesionId) => set({ sesionId }),
  setDescripcion: (descripcionProblema) => set({ descripcionProblema }),
  setModelo: (modelo) => set({ modelo }),
  setErroresValidacion: (erroresValidacion) => set({ erroresValidacion }),
  setResultado: (resultado) => set({ resultado }),
  setResultadoGrafico: (resultadoGrafico) => set({ resultadoGrafico }),
  setModeloTransporte: (modeloTransporte) => set({ modeloTransporte }),
  setModeloTransporteGrafico: (modeloTransporteGrafico) => set({ modeloTransporteGrafico }),
  setResultadoTransporte: (resultadoTransporte) => set({ resultadoTransporte }),
  setModeloRed: (modeloRed) => set({ modeloRed }),
  setModeloRedGrafico: (modeloRedGrafico) => set({ modeloRedGrafico }),
  setResultadoRed: (resultadoRed) => set({ resultadoRed }),
  setModeloEntero: (modeloEntero) => set({ modeloEntero }),
  setResultadoEntero: (resultadoEntero) => set({ resultadoEntero }),
  setModeloInventario: (modeloInventario) => set({ modeloInventario }),
  setResultadoInventario: (resultadoInventario) => set({ resultadoInventario }),
  setModeloDinamico: (modeloDinamico) => set({ modeloDinamico }),
  setResultadoDinamica: (resultadoDinamica) => set({ resultadoDinamica }),
  setValidado: (validado) => set({ validado }),
  resetResultado: () => set({ resultado: null, resultadoGrafico: null, resultadoTransporte: null, resultadoRed: null, resultadoEntero: null, resultadoInventario: null, resultadoDinamica: null, status: 'EDITING', validado: false }),
  setValidacion: (v) => set({
    erroresValidacion: v.erroresEncontrados,
    sugerenciasValidacion: v.sugerencias,
    validado: v.esValido,
  }),
  setIsChatBusy: (isChatBusy) => set({ isChatBusy }),
  setUltimaActualizacionIA: (ultimaActualizacionIA) => set(s => ({
    ultimaActualizacionIA,
    // Limpiar el aviso (null) no cuenta como entrega: no debe re-animar las cards.
    revisionIA: ultimaActualizacionIA === null ? s.revisionIA : s.revisionIA + 1,
  })),
}))
