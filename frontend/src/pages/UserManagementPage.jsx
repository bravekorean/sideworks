import { useEffect, useMemo, useState } from 'react'
import {
  assignAdminUser,
  changeAdminUserRole,
  changeAdminUserStatus,
  createAdminUser,
  getAdminDepartments,
  getAdminPositions,
  getAdminUser,
  getAdminUsers,
  updateAdminUser,
} from '../api/adminApi'

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

const jobFamilyLabels = {
  TECHNICAL: '기술직렬',
  CORPORATE: '경영직렬',
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function UserManagementPage() {
  const [users, setUsers] = useState([])
  const [departments, setDepartments] = useState([])
  const [positions, setPositions] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [assignmentFilter, setAssignmentFilter] = useState('ALL')
  const [selectedUser, setSelectedUser] = useState(null)
  const [dialogMode, setDialogMode] = useState(null)
  const [feedback, setFeedback] = useState('')
  const [detailFeedback, setDetailFeedback] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const loadManagementData = async () => {
    try {
      setIsLoading(true)
      const [userPage, departmentPage, positionPage] = await Promise.all([
        getAdminUsers(0, 100),
        getAdminDepartments(0, 100),
        getAdminPositions(0, 100),
      ])
      setUsers(userPage.content)
      setTotalElements(userPage.totalElements)
      setDepartments(departmentPage.content)
      setPositions(positionPage.content)
    } catch (error) {
      setFeedback(
        error.response?.data?.message ??
          '사용자 관리 정보를 불러오지 못했습니다.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    let isActive = true

    const loadInitialData = async () => {
      try {
        const [userPage, departmentPage, positionPage] = await Promise.all([
          getAdminUsers(0, 100),
          getAdminDepartments(0, 100),
          getAdminPositions(0, 100),
        ])
        if (isActive) {
          setUsers(userPage.content)
          setTotalElements(userPage.totalElements)
          setDepartments(departmentPage.content)
          setPositions(positionPage.content)
        }
      } catch (error) {
        if (isActive) {
          setFeedback(
            error.response?.data?.message ??
              '사용자 관리 정보를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) setIsLoading(false)
      }
    }

    loadInitialData()
    return () => {
      isActive = false
    }
  }, [])

  const filteredUsers = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    return users.filter((user) => {
      const matchesSearch = [
        user.loginId,
        user.userName,
        user.employeeNo,
        user.departmentName ?? '',
      ].some((value) => value.toLowerCase().includes(query))
      const matchesRole = roleFilter === 'ALL' || user.userRole === roleFilter
      const matchesStatus =
        statusFilter === 'ALL' || user.status === statusFilter
      const isAssigned = user.departmentId !== null && user.positionId !== null
      const matchesAssignment =
        assignmentFilter === 'ALL' ||
        (assignmentFilter === 'ASSIGNED' && isAssigned) ||
        (assignmentFilter === 'UNASSIGNED' && !isAssigned)
      return matchesSearch && matchesRole && matchesStatus && matchesAssignment
    })
  }, [assignmentFilter, roleFilter, searchQuery, statusFilter, users])

  const handleOpenUser = async (user) => {
    setSelectedUser(user)
    setDetailFeedback('')
    try {
      setIsDetailLoading(true)
      setSelectedUser(await getAdminUser(user.userId))
    } catch (error) {
      setDetailFeedback(
        error.response?.data?.message ??
          '사용자 상세 정보를 불러오지 못했습니다.',
      )
    } finally {
      setIsDetailLoading(false)
    }
  }

  const handleCreateSubmit = async (event) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const departmentId = data.get('departmentId')
    const positionId = data.get('positionId')

    try {
      setIsSubmitting(true)
      setFeedback('')
      const createdUser = await createAdminUser({
        loginId: data.get('loginId').trim(),
        password: data.get('password'),
        userName: data.get('userName').trim(),
        userEmail: data.get('userEmail').trim() || null,
        userPhone: data.get('userPhone').trim() || null,
        jobFamily: data.get('jobFamily'),
        hireDate: data.get('hireDate'),
        departmentId: departmentId ? Number(departmentId) : null,
        positionId: positionId ? Number(positionId) : null,
        userRole: data.get('userRole'),
        status: 'ACTIVE',
      })
      setDialogMode(null)
      await loadManagementData()
      setFeedback(`사용자를 생성했습니다. 발급 사번: ${createdUser.employeeNo}`)
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '사용자를 생성하지 못했습니다.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEditSubmit = async (event) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const departmentId = data.get('departmentId')
      ? Number(data.get('departmentId'))
      : null
    const positionId = data.get('positionId')
      ? Number(data.get('positionId'))
      : null
    const userRole = data.get('userRole') ?? selectedUser.userRole
    const status = data.get('status') ?? selectedUser.status

    try {
      setIsSubmitting(true)
      setFeedback('')
      await updateAdminUser(selectedUser.userId, {
        userName: data.get('userName').trim(),
        userEmail: data.get('userEmail').trim() || null,
        userPhone: data.get('userPhone').trim() || null,
      })

      if (
        departmentId !== selectedUser.departmentId ||
        positionId !== selectedUser.positionId
      ) {
        await assignAdminUser(selectedUser.userId, departmentId, positionId)
      }
      if (userRole !== selectedUser.userRole) {
        await changeAdminUserRole(selectedUser.userId, userRole)
      }
      if (status !== selectedUser.status) {
        await changeAdminUserStatus(selectedUser.userId, status)
      }

      const refreshedUser = await getAdminUser(selectedUser.userId)
      setSelectedUser(refreshedUser)
      setDialogMode(null)
      await loadManagementData()
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '사용자 정보를 수정하지 못했습니다.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const activeCount = users.filter((user) => user.status === 'ACTIVE').length
  const adminCount = users.filter((user) =>
    ['SUPER_ADMIN', 'ADMIN'].includes(user.userRole),
  ).length
  const unassignedCount = users.filter(
    (user) => user.departmentId === null || user.positionId === null,
  ).length

  return (
    <div className="admin-page">
      <header className="page-header">
        <div><span className="section-kicker">ADMIN · PEOPLE</span><h1>사용자 관리</h1><p>직원 계정과 소속, 직급, 권한 및 재직 상태를 관리합니다.</p></div>
        <button className="admin-primary-button" onClick={() => { setFeedback(''); setDialogMode('create') }} type="button">＋ 사용자 생성</button>
      </header>

      <section className="admin-summary-grid" aria-label="사용자 현황">
        <article className="admin-summary-card"><span>전체 사용자</span><strong>{totalElements}</strong><small>등록된 전체 계정</small></article>
        <article className="admin-summary-card"><span>재직 사용자</span><strong>{activeCount}</strong><small>현재 조회 범위 기준</small></article>
        <article className="admin-summary-card"><span>관리 권한</span><strong>{adminCount}</strong><small>SUPER_ADMIN · ADMIN</small></article>
        <article className="admin-summary-card admin-summary-card--warning"><span>인사 미배정</span><strong>{unassignedCount}</strong><small>부서 또는 직급 확인 필요</small></article>
      </section>

      {feedback && !dialogMode && <p className="create-user-feedback">{feedback}</p>}

      <section className="panel admin-table-panel">
        <div className="admin-toolbar">
          <label className="approval-search admin-user-search"><span aria-hidden="true">⌕</span><input onChange={(event) => setSearchQuery(event.target.value)} placeholder="이름, 로그인 ID, 사번, 부서 검색" type="search" value={searchQuery} /></label>
          <div className="admin-filter-group">
            <select aria-label="역할 필터" onChange={(event) => setRoleFilter(event.target.value)} value={roleFilter}><option value="ALL">모든 역할</option><option value="SUPER_ADMIN">최고 관리자</option><option value="ADMIN">관리자</option><option value="USER">일반 사용자</option></select>
            <select aria-label="상태 필터" onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}><option value="ALL">모든 상태</option><option value="ACTIVE">재직</option><option value="INACTIVE">비활성</option><option value="DELETED">삭제</option></select>
            <select aria-label="배정 상태 필터" onChange={(event) => setAssignmentFilter(event.target.value)} value={assignmentFilter}><option value="ALL">전체 배정 상태</option><option value="ASSIGNED">배정 완료</option><option value="UNASSIGNED">인사 미배정</option></select>
          </div>
        </div>

        <div className="approval-table-wrapper"><table className="approval-table admin-user-table">
          <thead><tr><th>사용자</th><th>로그인 ID</th><th>사번</th><th>부서</th><th>직급</th><th>역할</th><th>상태</th><th aria-label="상세 보기" /></tr></thead>
          <tbody>
            {isLoading ? <tr><td className="approval-empty-state" colSpan={8}>사용자 목록을 불러오는 중입니다.</td></tr> : filteredUsers.length ? filteredUsers.map((user) => <tr key={user.userId}>
              <td><button className="admin-user-cell" onClick={() => handleOpenUser(user)} type="button"><span>{user.userName.slice(0, 1)}</span><strong>{user.userName}</strong></button></td>
              <td>{user.loginId}</td><td className="approval-document-number">{user.employeeNo}</td>
              <td>{user.departmentName ?? <span className="unassigned-label">미배정</span>}</td><td>{user.positionName ?? <span className="unassigned-label">미배정</span>}</td>
              <td><span className={`role-badge role-badge--${user.userRole.toLowerCase()}`}>{roleLabels[user.userRole]}</span></td><td><span className={`user-status user-status--${user.status.toLowerCase()}`}>{statusLabels[user.status]}</span></td>
              <td><button aria-label={`${user.userName} 상세 보기`} className="row-detail-button" onClick={() => handleOpenUser(user)} type="button">›</button></td>
            </tr>) : <tr><td className="approval-empty-state" colSpan={8}><strong>조건에 맞는 사용자가 없습니다.</strong><span>검색어 또는 필터 조건을 변경해 보세요.</span></td></tr>}
          </tbody>
        </table></div>
        <footer className="admin-table-footer"><span>총 {filteredUsers.length}명 표시 · 서버 조회 {users.length}명</span><span>최대 100명 조회 후 화면에서 검색·필터</span></footer>
      </section>

      {selectedUser && dialogMode === null && <div className="admin-overlay" role="presentation">
        <button aria-label="사용자 상세 닫기" className="admin-overlay-backdrop" onClick={() => setSelectedUser(null)} type="button" />
        <aside aria-labelledby="user-detail-title" className="user-detail-drawer">
          <header className="user-detail-header"><div className="user-detail-avatar">{selectedUser.userName.slice(0, 1)}</div><div><span>{selectedUser.employeeNo}</span><h2 id="user-detail-title">{selectedUser.userName}</h2><p>{selectedUser.loginId}</p></div><button aria-label="닫기" onClick={() => setSelectedUser(null)} type="button">×</button></header>
          {isDetailLoading ? <p className="compose-feedback">상세 정보를 불러오는 중입니다.</p> : <>
            {detailFeedback && <p className="create-user-feedback">{detailFeedback}</p>}
            <div className="user-detail-section"><h3>계정 정보</h3><dl className="user-detail-list"><div><dt>이메일</dt><dd>{selectedUser.userEmail ?? '-'}</dd></div><div><dt>연락처</dt><dd>{selectedUser.userPhone ?? '-'}</dd></div><div><dt>직렬</dt><dd>{jobFamilyLabels[selectedUser.jobFamily] ?? '기존 계정'}</dd></div><div><dt>입사일</dt><dd>{selectedUser.hireDate ?? '-'}</dd></div><div><dt>권한</dt><dd>{roleLabels[selectedUser.userRole]}</dd></div><div><dt>상태</dt><dd>{statusLabels[selectedUser.status]}</dd></div></dl></div>
            <div className="user-detail-section"><h3>인사 배정</h3><div className="assignment-card"><div><span>부서</span><strong>{selectedUser.departmentName ?? '미배정'}</strong></div><div><span>직급</span><strong>{selectedUser.positionName ?? '미배정'}</strong></div></div></div>
            <div className="user-detail-section"><h3>시스템 기록</h3><dl className="user-detail-list"><div><dt>생성일</dt><dd>{formatDateTime(selectedUser.createdAt)}</dd></div><div><dt>수정일</dt><dd>{formatDateTime(selectedUser.updatedAt)}</dd></div></dl></div>
            <footer className="user-detail-actions"><button disabled={selectedUser.userRole === 'SUPER_ADMIN'} onClick={() => { setFeedback(''); setDialogMode('edit') }} type="button">상태·배정 변경</button><button className="admin-primary-button" onClick={() => { setFeedback(''); setDialogMode('edit') }} type="button">사용자 수정</button></footer>
          </>}
        </aside>
      </div>}

      {dialogMode === 'create' && <div className="decision-dialog-backdrop" role="presentation"><form aria-labelledby="create-user-title" className="decision-dialog create-user-dialog" onSubmit={handleCreateSubmit}>
        <header className="create-user-dialog__header"><div><span className="section-kicker">NEW USER</span><h2 id="create-user-title">사용자 생성</h2><p>부서와 직급은 계정 생성 후에도 배정할 수 있습니다.</p></div><button aria-label="닫기" onClick={() => setDialogMode(null)} type="button">×</button></header>
        <div className="create-user-form-grid">
          <label className="form-field"><span>이름</span><input name="userName" placeholder="직원 이름" required /></label><label className="form-field"><span>직렬</span><select defaultValue="TECHNICAL" name="jobFamily" required><option value="TECHNICAL">기술직렬 (TC)</option><option value="CORPORATE">경영직렬 (CP)</option></select></label>
          <label className="form-field"><span>로그인 ID</span><input name="loginId" placeholder="로그인 ID" required /></label><label className="form-field"><span>초기 비밀번호</span><input minLength="4" name="password" placeholder="초기 비밀번호" required type="password" /></label>
          <label className="form-field"><span>입사일</span><input name="hireDate" required type="date" /></label>
          <label className="form-field"><span>이메일</span><input name="userEmail" placeholder="name@example.com" type="email" /></label><label className="form-field"><span>연락처</span><input name="userPhone" placeholder="010-0000-0000" /></label>
          <label className="form-field"><span>부서</span><select defaultValue="" name="departmentId"><option value="">미배정</option>{departments.filter((item) => item.status === 'ACTIVE').map((item) => <option key={item.departmentId} value={item.departmentId}>{item.departmentName}</option>)}</select></label>
          <label className="form-field"><span>직급</span><select defaultValue="" name="positionId"><option value="">미배정</option>{positions.map((item) => <option key={item.positionId} value={item.positionId}>{item.positionName}</option>)}</select></label>
          <label className="form-field"><span>역할</span><select defaultValue="USER" name="userRole"><option value="USER">일반 사용자</option><option value="ADMIN">관리자</option></select></label>
        </div>
        <p aria-live="polite" className="create-user-feedback">{feedback}</p><footer className="decision-dialog__actions"><button disabled={isSubmitting} onClick={() => setDialogMode(null)} type="button">취소</button><button className="dialog-confirm" disabled={isSubmitting} type="submit">{isSubmitting ? '생성 중...' : '생성하기'}</button></footer>
      </form></div>}

      {dialogMode === 'edit' && selectedUser && <div className="decision-dialog-backdrop" role="presentation"><form aria-labelledby="edit-user-title" className="decision-dialog create-user-dialog" onSubmit={handleEditSubmit}>
        <header className="create-user-dialog__header"><div><span className="section-kicker">EDIT USER</span><h2 id="edit-user-title">사용자 정보 수정</h2><p>기본 정보와 인사 배정, 역할, 상태를 변경합니다.</p></div><button aria-label="닫기" onClick={() => setDialogMode(null)} type="button">×</button></header>
        <div className="create-user-form-grid">
          <label className="form-field"><span>이름</span><input defaultValue={selectedUser.userName} name="userName" required /></label><label className="form-field"><span>사번</span><input disabled value={selectedUser.employeeNo} /></label>
          <label className="form-field"><span>이메일</span><input defaultValue={selectedUser.userEmail ?? ''} name="userEmail" type="email" /></label><label className="form-field"><span>연락처</span><input defaultValue={selectedUser.userPhone ?? ''} name="userPhone" /></label>
          <label className="form-field"><span>부서</span><select defaultValue={selectedUser.departmentId ?? ''} name="departmentId">{selectedUser.departmentId === null && <option value="">미배정</option>}{departments.filter((item) => item.status === 'ACTIVE').map((item) => <option key={item.departmentId} value={item.departmentId}>{item.departmentName}</option>)}</select></label>
          <label className="form-field"><span>직급</span><select defaultValue={selectedUser.positionId ?? ''} name="positionId">{selectedUser.positionId === null && <option value="">미배정</option>}{positions.map((item) => <option key={item.positionId} value={item.positionId}>{item.positionName}</option>)}</select></label>
          <label className="form-field"><span>역할</span><select defaultValue={selectedUser.userRole} disabled={selectedUser.userRole === 'SUPER_ADMIN'} name="userRole">{selectedUser.userRole === 'SUPER_ADMIN' && <option value="SUPER_ADMIN">최고 관리자</option>}<option value="USER">일반 사용자</option><option value="ADMIN">관리자</option></select></label>
          <label className="form-field"><span>상태</span><select defaultValue={selectedUser.status} disabled={selectedUser.userRole === 'SUPER_ADMIN'} name="status"><option value="ACTIVE">재직</option><option value="INACTIVE">비활성</option><option value="DELETED">삭제</option></select></label>
        </div>
        <p aria-live="polite" className="create-user-feedback">{feedback}</p><footer className="decision-dialog__actions"><button disabled={isSubmitting} onClick={() => setDialogMode(null)} type="button">취소</button><button className="dialog-confirm" disabled={isSubmitting} type="submit">{isSubmitting ? '저장 중...' : '저장하기'}</button></footer>
      </form></div>}
    </div>
  )
}

export default UserManagementPage
