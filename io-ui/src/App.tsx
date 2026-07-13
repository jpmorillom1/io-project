import { Routes, Route } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { HomeWorkspace } from '@/pages/home/HomeWorkspace'
import { SimplexWorkspace } from '@/pages/lp/SimplexWorkspace'
import { TransporteWorkspace } from '@/pages/transporte/TransporteWorkspace'
import { RedesWorkspace } from '@/pages/redes/RedesWorkspace'
import { EnteraWorkspace } from '@/pages/entera/EnteraWorkspace'
import { InventarioWorkspace } from '@/pages/inventario/InventarioWorkspace'
import { DinamicaWorkspace } from '@/pages/dinamica/DinamicaWorkspace'

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomeWorkspace />} />
        <Route path="/home" element={<HomeWorkspace />} />
        <Route path="/lp" element={<SimplexWorkspace />} />
        <Route path="/transporte" element={<TransporteWorkspace />} />
        <Route path="/redes" element={<RedesWorkspace />} />
        <Route path="/pl-entera" element={<EnteraWorkspace />} />
        <Route path="/inventario" element={<InventarioWorkspace />} />
        <Route path="/dinamica" element={<DinamicaWorkspace />} />
      </Route>
    </Routes>
  )
}
