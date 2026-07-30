import { useMemo, useState } from 'react'

const users = [
  {
    userId: 1,
    loginId: 'superadmin',
    userName: '이총괄',
    employeeNo: 'EMP001',
    userEmail: 'chief@sideworks.local',
    userPhone: '010-1000-1000',
    departmentId: 1,
    departmentName: '경영지원본부',
    positionId: 5,
    positionName: '본부장',
    userRole: 'SUPER_ADMIN',
    status: 'ACTIVE',
    createdAt: '2026-07-01 09:00',
    updatedAt: '2026-07-22 14:31',
  },
  {
    userId: 2,
    loginId: 'admin',
    userName: '김관리',
    employeeNo: 'EMP002',
    userEmail: 'admin@sideworks.local',
    userPhone: '010-2000-2000',
    departmentId: 2,
    departmentName: '개발팀',
    positionId: 4,
    positionName: '팀장',
    userRole: 'ADMIN',
    status: 'ACTIVE',
    createdAt: '2026-07-01 09:20',
    updatedAt: '2026-07-28 11:12',
  },
  {
    userId: 3,
    loginId: 'junho',
    userName: '박준호',
    employeeNo: 'EMP003',
    userEmail: 'junho@sideworks.local',
    userPhone: '010-3000-3000',
    departmentId: 2,
    departmentName: '개발팀',
    positionId: 1,
    positionName: '사원',
    userRole: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-07-04 10:15',
    updatedAt: '2026-07-27 17:45',
  },
  {
    userId: 4,
    loginId: 'seoyun',
    userName: '이서윤',
    employeeNo: 'EMP004',
    userEmail: 'seoyun@sideworks.local',
    userPhone: '010-4000-4000',
    departmentId: 3,
    departmentName: '인사팀',
    positionId: 2,
    positionName: '대리',
    userRole: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-07-08 13:20',
    updatedAt: '2026-07-25 09:18',
  },
  {
    userId: 5,
    loginId: 'minho',
    userName: '최민호',
    employeeNo: 'EMP005',
    userEmail: 'minho@sideworks.local',
    userPhone: '010-5000-5000',
    departmentId: 4,
    departmentName: '재무팀',
    positionId: 4,
    positionName: '팀장',
    userRole: 'ADMIN',
    status: 'INACTIVE',
    createdAt: '2026-07-09 11:03',
    updatedAt: '2026-07-29 08:50',
  },
  {
    userId: 6,
    loginId: 'newhire',
    userName: '한신입',
    employeeNo: 'EMP006',
    userEmail: 'newhire@sideworks.local',
    userPhone: '010-6000-6000',
    departmentId: null,
    departmentName: null,
    positionId: null,
    positionName: null,
    userRole: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-07-29 09:10',
    updatedAt: '2026-07-29 09:10',
  },
]

const roleLabels = {
  SUPER_ADMIN: '최고 관리자',
  ADMIN: '관리자',
  USER: '일반 사용자',
}

const statusLabels = {
  ACTIVE: '재직',
  INACTIVE: '비활성',
  DELETED: '삭제',
}

function UserManagementPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [assignmentFilter, setAssignmentFilter] = useState('ALL')
  const [selectedUser, setSelectedUser] = useState(null)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [feedback, setFeedback] = useState('')

  const filteredUsers = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase()

    return users.filter((user) => {
      const matchesSearch = [
        user.loginId,
        user.userName,
        user.employeeNo,
        user.departmentName ?? '',
      ].some((value) => value.toLowerCase().includes(normalizedQuery))
      const matchesRole =
        roleFilter === 'ALL' || user.userRole === roleFilter
      const matchesStatus =
        statusFilter === 'ALL' || user.status === statusFilter
      const isAssigned = user.departmentId !== null && user.positionId !== null
      const matchesAssignment =
        assignmentFilter === 'ALL' ||
        (assignmentFilter === 'ASSIGNED' && isAssigned) ||
        (assignmentFilter === 'UNASSIGNED' && !isAssigned)

      return (
        matchesSearch &&
        matchesRole &&
        matchesStatus &&
        matchesAssignment
      )
    })
  }, [assignmentFilter, roleFilter, searchQuery, statusFilter])

  const activeCount = users.filter((user) => user.status === 'ACTIVE').length
  const adminCount = users.filter((user) =>
    ['SUPER_ADMIN', 'ADMIN'].includes(user.userRole),
  ).length
  const unassignedCount = users.filter(
    (user) => user.departmentId === null || user.positionId === null,
  ).length

  const handleCreateSubmit = (event) => {
    event.preventDefault()
    setFeedback(
      '입력 형식 확인이 완료되었습니다. 실제 생성은 API 연결 후 처리됩니다.',
    )
  }

  return (
    <div className="admin-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">ADMIN · PEOPLE</span>
          <h1>사용자 관리</h1>
          <p>직원 계정과 소속, 직급, 권한 및 재직 상태를 관리합니다.</p>
        </div>
        <button
          className="admin-primary-button"
          onClick={() => {
            setFeedback('')
            setCreateDialogOpen(true)
          }}
          type="button"
        >
          <span aria-hidden="true">＋</span>
          사용자 생성
        </button>
      </header>

      <section className="admin-summary-grid" aria-label="사용자 현황">
        <article className="admin-summary-card">
          <span>전체 사용자</span>
          <strong>{users.length}</strong>
          <small>등록된 전체 계정</small>
        </article>
        <article className="admin-summary-card">
          <span>재직 사용자</span>
          <strong>{activeCount}</strong>
          <small>현재 로그인 가능</small>
        </article>
        <article className="admin-summary-card">
          <span>관리 권한</span>
          <strong>{adminCount}</strong>
          <small>SUPER_ADMIN · ADMIN</small>
        </article>
        <article className="admin-summary-card admin-summary-card--warning">
          <span>인사 미배정</span>
          <strong>{unassignedCount}</strong>
          <small>부서 또는 직급 확인 필요</small>
        </article>
      </section>

      <section className="panel admin-table-panel">
        <div className="admin-toolbar">
          <label className="approval-search admin-user-search">
            <span aria-hidden="true">⌕</span>
            <input
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="이름, 로그인 ID, 사번, 부서 검색"
              type="search"
              value={searchQuery}
            />
          </label>

          <div className="admin-filter-group">
            <select
              aria-label="역할 필터"
              onChange={(event) => setRoleFilter(event.target.value)}
              value={roleFilter}
            >
              <option value="ALL">모든 역할</option>
              <option value="SUPER_ADMIN">최고 관리자</option>
              <option value="ADMIN">관리자</option>
              <option value="USER">일반 사용자</option>
            </select>
            <select
              aria-label="상태 필터"
              onChange={(event) => setStatusFilter(event.target.value)}
              value={statusFilter}
            >
              <option value="ALL">모든 상태</option>
              <option value="ACTIVE">재직</option>
              <option value="INACTIVE">비활성</option>
              <option value="DELETED">삭제</option>
            </select>
            <select
              aria-label="배정 상태 필터"
              onChange={(event) => setAssignmentFilter(event.target.value)}
              value={assignmentFilter}
            >
              <option value="ALL">전체 배정 상태</option>
              <option value="ASSIGNED">배정 완료</option>
              <option value="UNASSIGNED">인사 미배정</option>
            </select>
          </div>
        </div>

        <div className="approval-table-wrapper">
          <table className="approval-table admin-user-table">
            <thead>
              <tr>
                <th>사용자</th>
                <th>로그인 ID</th>
                <th>사번</th>
                <th>부서</th>
                <th>직급</th>
                <th>역할</th>
                <th>상태</th>
                <th aria-label="상세 보기" />
              </tr>
            </thead>
            <tbody>
              {filteredUsers.length > 0 ? (
                filteredUsers.map((user) => (
                  <tr key={user.userId}>
                    <td>
                      <button
                        className="admin-user-cell"
                        onClick={() => setSelectedUser(user)}
                        type="button"
                      >
                        <span>{user.userName.slice(0, 1)}</span>
                        <strong>{user.userName}</strong>
                      </button>
                    </td>
                    <td>{user.loginId}</td>
                    <td className="approval-document-number">
                      {user.employeeNo}
                    </td>
                    <td>
                      {user.departmentName ?? (
                        <span className="unassigned-label">미배정</span>
                      )}
                    </td>
                    <td>
                      {user.positionName ?? (
                        <span className="unassigned-label">미배정</span>
                      )}
                    </td>
                    <td>
                      <span
                        className={`role-badge role-badge--${user.userRole.toLowerCase()}`}
                      >
                        {roleLabels[user.userRole]}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`user-status user-status--${user.status.toLowerCase()}`}
                      >
                        {statusLabels[user.status]}
                      </span>
                    </td>
                    <td>
                      <button
                        aria-label={`${user.userName} 상세 보기`}
                        className="row-detail-button"
                        onClick={() => setSelectedUser(user)}
                        type="button"
                      >
                        ›
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="approval-empty-state" colSpan={8}>
                    <strong>조건에 맞는 사용자가 없습니다.</strong>
                    <span>검색어 또는 필터 조건을 변경해 보세요.</span>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="admin-table-footer">
          <span>
            총 {filteredUsers.length}명 · 목업 데이터 기준
          </span>
          <span>페이지네이션은 API 응답의 page 정보와 연결 예정</span>
        </footer>
      </section>

      {selectedUser && (
        <div className="admin-overlay" role="presentation">
          <button
            aria-label="사용자 상세 닫기"
            className="admin-overlay-backdrop"
            onClick={() => setSelectedUser(null)}
            type="button"
          />
          <aside
            aria-labelledby="user-detail-title"
            className="user-detail-drawer"
          >
            <header className="user-detail-header">
              <div className="user-detail-avatar">
                {selectedUser.userName.slice(0, 1)}
              </div>
              <div>
                <span>{selectedUser.employeeNo}</span>
                <h2 id="user-detail-title">{selectedUser.userName}</h2>
                <p>{selectedUser.loginId}</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setSelectedUser(null)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="user-detail-section">
              <h3>계정 정보</h3>
              <dl className="user-detail-list">
                <div>
                  <dt>이메일</dt>
                  <dd>{selectedUser.userEmail}</dd>
                </div>
                <div>
                  <dt>연락처</dt>
                  <dd>{selectedUser.userPhone}</dd>
                </div>
                <div>
                  <dt>권한</dt>
                  <dd>{roleLabels[selectedUser.userRole]}</dd>
                </div>
                <div>
                  <dt>상태</dt>
                  <dd>{statusLabels[selectedUser.status]}</dd>
                </div>
              </dl>
            </div>

            <div className="user-detail-section">
              <h3>인사 배정</h3>
              <div className="assignment-card">
                <div>
                  <span>부서</span>
                  <strong>{selectedUser.departmentName ?? '미배정'}</strong>
                </div>
                <div>
                  <span>직급</span>
                  <strong>{selectedUser.positionName ?? '미배정'}</strong>
                </div>
              </div>
            </div>

            <div className="user-detail-section">
              <h3>시스템 기록</h3>
              <dl className="user-detail-list">
                <div>
                  <dt>생성일</dt>
                  <dd>{selectedUser.createdAt}</dd>
                </div>
                <div>
                  <dt>수정일</dt>
                  <dd>{selectedUser.updatedAt}</dd>
                </div>
              </dl>
            </div>

            <footer className="user-detail-actions">
              <button type="button">상태 변경</button>
              <button className="admin-primary-button" type="button">
                사용자 수정
              </button>
            </footer>
          </aside>
        </div>
      )}

      {createDialogOpen && (
        <div className="decision-dialog-backdrop" role="presentation">
          <form
            aria-labelledby="create-user-title"
            className="decision-dialog create-user-dialog"
            onSubmit={handleCreateSubmit}
          >
            <header className="create-user-dialog__header">
              <div>
                <span className="section-kicker">NEW USER</span>
                <h2 id="create-user-title">사용자 생성</h2>
                <p>계정 생성 후 부서와 직급은 나중에 배정할 수 있습니다.</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setCreateDialogOpen(false)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="create-user-form-grid">
              <label className="form-field">
                <span>이름</span>
                <input placeholder="직원 이름" required />
              </label>
              <label className="form-field">
                <span>사번</span>
                <input placeholder="EMP000" required />
              </label>
              <label className="form-field">
                <span>로그인 ID</span>
                <input placeholder="로그인 ID" required />
              </label>
              <label className="form-field">
                <span>초기 비밀번호</span>
                <input placeholder="초기 비밀번호" required type="password" />
              </label>
              <label className="form-field">
                <span>이메일</span>
                <input placeholder="name@example.com" type="email" />
              </label>
              <label className="form-field">
                <span>역할</span>
                <select defaultValue="USER">
                  <option value="USER">일반 사용자</option>
                  <option value="ADMIN">관리자</option>
                  <option value="SUPER_ADMIN">최고 관리자</option>
                </select>
              </label>
            </div>

            <p aria-live="polite" className="create-user-feedback">
              {feedback}
            </p>

            <footer className="decision-dialog__actions">
              <button
                onClick={() => setCreateDialogOpen(false)}
                type="button"
              >
                취소
              </button>
              <button className="dialog-confirm" type="submit">
                생성하기
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  )
}

export default UserManagementPage
