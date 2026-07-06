import { Routes, Route, Navigate } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { SimplexWorkspace } from '@/pages/lp/SimplexWorkspace'
import { TransporteWorkspace } from '@/pages/transporte/TransporteWorkspace'
import { RedesWorkspace } from '@/pages/redes/RedesWorkspace'

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/lp" replace />} />
        <Route path="/lp" element={<SimplexWorkspace />} />
        <Route path="/transporte" element={<TransporteWorkspace />} />
        <Route path="/redes" element={<RedesWorkspace />} />
      </Route>
    </Routes>
  )
}
