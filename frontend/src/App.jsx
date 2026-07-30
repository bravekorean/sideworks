import { Navigate, Route, Routes } from 'react-router'
import AppShell from './components/AppShell'
import ApprovalDetailPage from './pages/ApprovalDetailPage'
import ApprovalBoxPage from './pages/ApprovalBoxPage'
import DashboardPage from './pages/DashboardPage'
import DepartmentManagementPage from './pages/DepartmentManagementPage'
import NewApprovalPage from './pages/NewApprovalPage'
import PendingApprovalPage from './pages/PendingApprovalPage'
import PositionManagementPage from './pages/PositionManagementPage'
import UserManagementPage from './pages/UserManagementPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/approvals/new" element={<NewApprovalPage />} />
        <Route
          path="/approvals/drafts"
          element={<ApprovalBoxPage box="drafts" />}
        />
        <Route path="/approvals/sent" element={<ApprovalBoxPage box="sent" />} />
        <Route path="/approvals/pending" element={<PendingApprovalPage />} />
        <Route
          path="/approvals/processed"
          element={<ApprovalBoxPage box="processed" />}
        />
        <Route path="/approvals/cc" element={<ApprovalBoxPage box="cc" />} />
        <Route path="/approvals/:approvalId" element={<ApprovalDetailPage />} />
        <Route path="/admin/users" element={<UserManagementPage />} />
        <Route
          path="/admin/departments"
          element={<DepartmentManagementPage />}
        />
        <Route
          path="/admin/positions"
          element={<PositionManagementPage />}
        />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
