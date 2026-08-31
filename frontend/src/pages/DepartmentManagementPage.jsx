import { useEffect, useMemo, useState } from 'react'
import {
  createDepartment,
  deleteDepartment,
  getAdminDepartments,
  getAdminUsers,
  updateDepartment,
  updateDepartmentManager,
} from '../api/adminApi'

function getDepartmentLevel(department, departments, visited = new Set()) {
  if (!department.parentDepartmentId || visited.has(department.departmentId)) {
    return 0
  }

  const parent = departments.find(
    (item) => item.departmentId === department.parentDepartmentId,
  )
  if (!parent) return 0

  const nextVisited = new Set(visited)
  nextVisited.add(department.departmentId)
  return 1 + getDepartmentLevel(parent, departments, nextVisited)
}

function orderDepartmentsAsTree(departments) {
  const departmentIds = new Set(
    departments.map((department) => department.departmentId),
  )
  const childrenByParentId = new Map()
  const orderedDepartments = []
  const visited = new Set()
  const sortedDepartments = [...departments].sort((left, right) => {
    const nameOrder = left.departmentName.localeCompare(
      right.departmentName,
      'ko',
    )

    return nameOrder || left.departmentId - right.departmentId
  })

  sortedDepartments.forEach((department) => {
    const hasValidParent = departmentIds.has(department.parentDepartmentId)
    const parentId = hasValidParent ? department.parentDepartmentId : null
    const children = childrenByParentId.get(parentId) ?? []
    children.push(department)
    childrenByParentId.set(parentId, children)
  })

  const appendDepartmentAndChildren = (department) => {
    if (visited.has(department.departmentId)) return

    visited.add(department.departmentId)
    orderedDepartments.push(department)
    const children = childrenByParentId.get(department.departmentId) ?? []
    children.forEach(appendDepartmentAndChildren)
  }

  const rootDepartments = childrenByParentId.get(null) ?? []
  rootDepartments.forEach(appendDepartmentAndChildren)

  // 잘못된 순환 관계가 있어도 해당 부서를 화면에서 누락하지 않는다.
  sortedDepartments.forEach(appendDepartmentAndChildren)

  return orderedDepartments
}

function DepartmentManagementPage() {
  const [departments, setDepartments] = useState([])
  const [users, setUsers] = useState([])
  const [selectedDepartmentId, setSelectedDepartmentId] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [dialogMode, setDialogMode] = useState(null)
  const [feedback, setFeedback] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const applyManagementData = (departmentPage, userPage) => {
    setDepartments(departmentPage.content)
    setUsers(userPage.content)
    setSelectedDepartmentId((currentId) =>
      departmentPage.content.some((item) => item.departmentId === currentId)
        ? currentId
        : (departmentPage.content[0]?.departmentId ?? null),
    )
  }

  const loadManagementData = async () => {
    try {
      setIsLoading(true)
      const responses = await Promise.all([
        getAdminDepartments(),
        getAdminUsers(),
      ])
      applyManagementData(...responses)
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '조직 정보를 불러오지 못했습니다.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    let isActive = true

    const loadInitialData = async () => {
      try {
        const [departmentPage, userPage] = await Promise.all([
          getAdminDepartments(),
          getAdminUsers(),
        ])
        if (isActive) {
          setDepartments(departmentPage.content)
          setUsers(userPage.content)
          setSelectedDepartmentId(
            departmentPage.content[0]?.departmentId ?? null,
          )
        }
      } catch (error) {
        if (isActive) {
          setFeedback(
            error.response?.data?.message ??
              '조직 정보를 불러오지 못했습니다.',
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

  const enrichedDepartments = useMemo(
    () =>
      orderDepartmentsAsTree(departments).map((department) => ({
        ...department,
        level: getDepartmentLevel(department, departments),
        managerName:
          users.find((user) => user.userId === department.managerUserId)
            ?.userName ?? null,
        memberCount: users.filter(
          (user) => user.departmentId === department.departmentId,
        ).length,
      })),
    [departments, users],
  )

  const selectedDepartment = enrichedDepartments.find(
    (department) => department.departmentId === selectedDepartmentId,
  )
  const childDepartments = enrichedDepartments.filter(
    (department) =>
      department.parentDepartmentId === selectedDepartment?.departmentId,
  )
  const filteredDepartments = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    return enrichedDepartments.filter(
      (department) =>
        [department.departmentName, department.managerName ?? ''].some(
          (value) => value.toLowerCase().includes(query),
        ) &&
        (statusFilter === 'ALL' || department.status === statusFilter),
    )
  }, [enrichedDepartments, searchQuery, statusFilter])

  const openDialog = (mode) => {
    setFeedback('')
    setDialogMode(mode)
  }

  const handleDialogSubmit = async (event) => {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    const departmentName = formData.get('departmentName').trim()
    const parentDepartmentId = formData.get('parentDepartmentId')
      ? Number(formData.get('parentDepartmentId'))
      : null
    const managerUserId = formData.get('managerUserId')
      ? Number(formData.get('managerUserId'))
      : null

    try {
      setIsSubmitting(true)
      let departmentId = selectedDepartment?.departmentId
      if (dialogMode === 'create') {
        departmentId = await createDepartment(
          departmentName,
          parentDepartmentId,
        )
      } else {
        await updateDepartment(
          departmentId,
          departmentName,
          parentDepartmentId,
        )
      }
      await updateDepartmentManager(departmentId, managerUserId)
      setDialogMode(null)
      setSelectedDepartmentId(departmentId)
      await loadManagementData()
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '부서 정보를 저장하지 못했습니다.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (
      !selectedDepartment ||
      !window.confirm(
        `${selectedDepartment.departmentName} 부서를 비활성 처리할까요?`,
      )
    ) return

    try {
      await deleteDepartment(selectedDepartment.departmentId)
      await loadManagementData()
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '부서를 비활성 처리하지 못했습니다.',
      )
    }
  }

  const parentName =
    enrichedDepartments.find(
      (item) => item.departmentId === selectedDepartment?.parentDepartmentId,
    )?.departmentName ?? '최상위 부서'
  const managerCandidates = users.filter(
    (user) =>
      user.status === 'ACTIVE' &&
      ['ADMIN', 'SUPER_ADMIN'].includes(user.userRole),
  )

  return (
    <div className="admin-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">ADMIN · ORGANIZATION</span>
          <h1>부서 관리</h1>
          <p>계층형 조직 구조와 부서장, 운영 상태를 관리합니다.</p>
        </div>
        <button className="admin-primary-button" onClick={() => openDialog('create')} type="button">＋ 부서 생성</button>
      </header>

      <section className="organization-summary">
        <div><span>전체 부서</span><strong>{departments.length}</strong></div>
        <div><span>운영 중</span><strong>{departments.filter((item) => item.status === 'ACTIVE').length}</strong></div>
        <div><span>부서장 미지정</span><strong>{departments.filter((item) => item.managerUserId === null).length}</strong></div>
        <p>부서명은 중복될 수 있으며, 고유 식별은 부서 ID를 사용합니다.</p>
      </section>

      {feedback && !dialogMode && <p className="create-user-feedback">{feedback}</p>}

      <div className="department-management-grid">
        <section className="panel department-tree-panel">
          <div className="admin-toolbar">
            <label className="approval-search"><span aria-hidden="true">⌕</span><input onChange={(event) => setSearchQuery(event.target.value)} placeholder="부서명 또는 부서장 검색" type="search" value={searchQuery} /></label>
            <select aria-label="부서 상태 필터" className="management-filter" onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}><option value="ALL">전체 상태</option><option value="ACTIVE">운영 중</option><option value="INACTIVE">비활성</option></select>
          </div>
          <div className="department-tree-heading"><span>조직 구조</span><span>부서장</span><span>인원</span><span>상태</span></div>
          <div className="department-tree">
            {isLoading && <p className="compose-feedback">불러오는 중입니다.</p>}
            {filteredDepartments.map((department) => (
              <button className={`department-tree-row ${selectedDepartmentId === department.departmentId ? 'department-tree-row--selected' : ''}`} key={department.departmentId} onClick={() => setSelectedDepartmentId(department.departmentId)} type="button">
                <span className="department-tree-name" style={{ '--tree-level': department.level }}>{department.level > 0 && <span className="tree-connector" aria-hidden="true">└</span>}<span className="department-folder" aria-hidden="true">{department.level < 2 ? '▣' : '□'}</span><span><strong>{department.departmentName}</strong><small>ID {department.departmentId}</small></span></span>
                <span>{department.managerName ?? '미지정'}</span><span>{department.memberCount}명</span><span className={`user-status user-status--${department.status.toLowerCase()}`}>{department.status === 'ACTIVE' ? '운영 중' : '비활성'}</span>
              </button>
            ))}
          </div>
        </section>

        {selectedDepartment && <aside className="panel department-detail-panel">
          <header><div><span className="section-kicker">DEPARTMENT DETAIL</span><h2>{selectedDepartment.departmentName}</h2><p>부서 ID · {selectedDepartment.departmentId}</p></div><button className="management-edit-button" onClick={() => openDialog('edit')} type="button">수정</button></header>
          <dl className="department-detail-list"><div><dt>상위 부서</dt><dd>{parentName}</dd></div><div><dt>부서장</dt><dd>{selectedDepartment.managerName ?? '미지정'}</dd></div><div><dt>소속 인원</dt><dd>{selectedDepartment.memberCount}명</dd></div><div><dt>운영 상태</dt><dd>{selectedDepartment.status === 'ACTIVE' ? '운영 중' : '비활성'}</dd></div></dl>
          <section className="department-child-section"><header><h3>하위 부서</h3><span>{childDepartments.length}</span></header>{childDepartments.length ? <ul>{childDepartments.map((department) => <li key={department.departmentId}><span>{department.departmentName}</span><small>{department.memberCount}명</small></li>)}</ul> : <p>등록된 하위 부서가 없습니다.</p>}</section>
          <footer className="department-detail-actions"><button onClick={() => openDialog('edit')} type="button">부서장 변경</button><button className="danger-outline-button" disabled={selectedDepartment.status !== 'ACTIVE'} onClick={handleDelete} type="button">비활성 처리</button></footer>
        </aside>}
      </div>

      {dialogMode && <div className="decision-dialog-backdrop" role="presentation">
        <form aria-labelledby="department-dialog-title" className="decision-dialog management-dialog" onSubmit={handleDialogSubmit}>
          <header className="create-user-dialog__header"><div><span className="section-kicker">{dialogMode === 'create' ? 'NEW DEPARTMENT' : 'EDIT DEPARTMENT'}</span><h2 id="department-dialog-title">{dialogMode === 'create' ? '부서 생성' : '부서 정보 수정'}</h2><p>상위 부서를 선택하여 조직 계층을 구성합니다.</p></div><button aria-label="닫기" onClick={() => setDialogMode(null)} type="button">×</button></header>
          <div className="management-form">
            <label className="form-field"><span>부서명</span><input defaultValue={dialogMode === 'edit' ? selectedDepartment?.departmentName : ''} name="departmentName" placeholder="부서명을 입력하세요." required /></label>
            <label className="form-field"><span>상위 부서</span><select defaultValue={dialogMode === 'edit' ? (selectedDepartment?.parentDepartmentId ?? '') : ''} name="parentDepartmentId"><option value="">최상위 부서</option>{departments.filter((item) => item.departmentId !== selectedDepartment?.departmentId && item.status === 'ACTIVE').map((department) => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</select></label>
            <label className="form-field"><span>부서장</span><select defaultValue={dialogMode === 'edit' ? (selectedDepartment?.managerUserId ?? '') : ''} name="managerUserId"><option value="">미지정</option>{managerCandidates.map((user) => <option key={user.userId} value={user.userId}>{user.userName} · {user.loginId}</option>)}</select></label>
          </div>
          <p aria-live="polite" className="create-user-feedback">{feedback}</p>
          <footer className="decision-dialog__actions"><button disabled={isSubmitting} onClick={() => setDialogMode(null)} type="button">취소</button><button className="dialog-confirm" disabled={isSubmitting} type="submit">{isSubmitting ? '저장 중...' : dialogMode === 'create' ? '생성하기' : '저장하기'}</button></footer>
        </form>
      </div>}
    </div>
  )
}

export default DepartmentManagementPage
