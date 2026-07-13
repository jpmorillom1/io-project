import { Outlet } from 'react-router'
import { ModuleRail } from './ModuleRail'
import { TopBar } from './TopBar'
import { Logo } from './Logo'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { ChatProvider } from '@/context/ChatProvider'

const RAIL_WIDTH = '44px'

export function AppShell() {
  return (
    // El fondo del shell es plano a propósito: es el hueco entre paneles de un IDE,
    // no un lienzo. El único elemento ambiental de la app vive en el home
    // (`ShaderBackdrop`); un degradado aquí competiría con él y ensuciaría las cards.
    <div
      className="h-screen flex flex-col overflow-hidden"
      style={{ background: 'var(--ij-bg-secondary)' }}
    >
      {/* Fila superior: logo (fusionado con el rail) + toolbar */}
      <div className="flex h-9 shrink-0">
        <div
          className="flex items-center justify-center shrink-0"
          style={{ width: RAIL_WIDTH }}
        >
          <Logo />
        </div>
        <TopBar />
      </div>

      {/* Fila inferior: rail de módulos + chat + contenido del módulo.
          El chat vive en un contexto por encima de las rutas: cambiar de módulo
          (manual o adaptativo) NO reinicia la conversación.

          Y el ChatPanel se monta AQUÍ, no dentro de cada workspace. Renderizarlo en
          las siete páginas lo desmontaba y remontaba en cada navegación: se perdía el
          borrador del input, el scroll y el panel de historial abierto, y el avatar
          destruía y recreaba su contexto WebGL. El <Outlet> solo cambia el panel derecho. */}
      <ChatProvider>
        <div className="flex flex-1 overflow-hidden">
          <ModuleRail />

          <div className="flex flex-1 h-full overflow-hidden gap-3">
            <div
              className="w-[420px] shrink-0 flex flex-col overflow-hidden rounded-[10px]"
              style={{
                background: 'var(--ij-bg-editor)',
                boxShadow: '0 0 0 1px var(--ij-bg-editor)',
              }}
            >
              <ChatPanel />
            </div>

            <div className="flex-1 h-full overflow-hidden">
              <Outlet />
            </div>
          </div>
        </div>
      </ChatProvider>
    </div>
  )
}
