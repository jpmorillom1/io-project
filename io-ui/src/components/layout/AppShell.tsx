import { Outlet } from 'react-router'
import { ModuleRail } from './ModuleRail'
import { TopBar } from './TopBar'
import { Logo } from './Logo'
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

      {/* Fila inferior: rail de módulos + contenido.
          El chat vive en un contexto por encima de las rutas: cambiar de módulo
          (manual o adaptativo) NO reinicia la conversación. */}
      <ChatProvider>
        <div className="flex flex-1 overflow-hidden">
          <ModuleRail />
          <div className="flex-1 h-full overflow-hidden">
            <Outlet />
          </div>
        </div>
      </ChatProvider>
    </div>
  )
}
