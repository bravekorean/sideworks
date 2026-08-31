import { Navigate, Route, Routes } from 'react-router'
import AppShell from './components/AppShell'
import ApprovalDetailPage from './pages/ApprovalDetailPage'
import ApprovalBoxPage from './pages/ApprovalBoxPage'
import DashboardPage from './pages/DashboardPage'
import DepartmentManagementPage from './pages/DepartmentManagementPage'
import ErrorPage from './pages/ErrorPage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import NewApprovalPage from './pages/NewApprovalPage'
import PendingApprovalPage from './pages/PendingApprovalPage'
import PositionManagementPage from './pages/PositionManagementPage'
import SearchResultsPage from './pages/SearchResultsPage'
import UserManagementPage from './pages/UserManagementPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/approvals/new" element={<NewApprovalPage />} />
        <Route
          path="/approvals/:approvalId/edit"
          element={<NewApprovalPage />}
        />
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
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/search" element={<SearchResultsPage />} />
        <Route
          path="/forbidden"
          element={
            <ErrorPage
              code="403"
              description="현재 계정에는 이 화면을 조회할 권한이 없습니다. 필요한 경우 관리자에게 권한을 요청하세요."
              title="접근 권한이 없습니다."
            />
          }
        />
        <Route
          path="*"
          element={
            <ErrorPage
              code="404"
              description="요청한 주소가 변경되었거나 존재하지 않습니다. 주소를 확인하거나 대시보드로 이동하세요."
              title="페이지를 찾을 수 없습니다."
            />
          }
        />
      </Route>

      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
