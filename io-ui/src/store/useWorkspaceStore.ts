import { create } from 'zustand'
import type {
  ModeloLP, SolveResult, SolveResultGrafico, WorkspaceStatus, ValidacionResponse,
  ModeloTransporte, SolveResultTransporte, ModeloRed, SolveResultRed,
  ModeloEntero, SolveResultEntera,
  ModeloInventario, SolveResultInventario,
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
  validado: boolean
  isChatBusy: boolean
  ultimaActualizacionIA: ActualizacionIA

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
  validado: false,
  isChatBusy: false,
  ultimaActualizacionIA: null,

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
  setValidado: (validado) => set({ validado }),
  resetResultado: () => set({ resultado: null, resultadoGrafico: null, resultadoTransporte: null, resultadoRed: null, resultadoEntero: null, resultadoInventario: null, status: 'EDITING', validado: false }),
  setValidacion: (v) => set({
    erroresValidacion: v.erroresEncontrados,
    sugerenciasValidacion: v.sugerencias,
    validado: v.esValido,
  }),
  setIsChatBusy: (isChatBusy) => set({ isChatBusy }),
  setUltimaActualizacionIA: (ultimaActualizacionIA) => set({ ultimaActualizacionIA }),
}))
