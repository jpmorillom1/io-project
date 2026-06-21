import { Routes, Route } from 'react-router'
import { Home } from '@/pages/Home'
import { SimplexWorkspace } from '@/pages/lp/SimplexWorkspace'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/lp" element={<SimplexWorkspace />} />
    </Routes>
  )
}
