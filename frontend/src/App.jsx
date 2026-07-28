import { Navigate, Route, Routes } from 'react-router'
import AppShell from './components/AppShell'
import DashboardPage from './pages/DashboardPage'
import PendingApprovalPage from './pages/PendingApprovalPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/approvals/pending" element={<PendingApprovalPage />} />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App