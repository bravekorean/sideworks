import { useMemo, useState } from 'react'

const departments = [
  {
    departmentId: 1,
    parentDepartmentId: null,
    departmentName: 'SideWorks',
    managerUserId: 1,
    managerName: '이총괄',
    status: 'ACTIVE',
    memberCount: 18,
    level: 0,
  },
  {
    departmentId: 2,
    parentDepartmentId: 1,
    departmentName: '개발본부',
    managerUserId: 2,
    managerName: '김관리',
    status: 'ACTIVE',
    memberCount: 9,
    level: 1,
  },
  {
    departmentId: 3,
    parentDepartmentId: 2,
    departmentName: '백엔드팀',
    managerUserId: 3,
    managerName: '박준호',
    status: 'ACTIVE',
    memberCount: 4,
    level: 2,
  },
  {
    departmentId: 4,
    parentDepartmentId: 2,
    departmentName: '프론트엔드팀',
    managerUserId: null,
    managerName: null,
    status: 'ACTIVE',
    memberCount: 3,
    level: 2,
  },
  {
    departmentId: 5,
    parentDepartmentId: 2,
    departmentName: 'QA팀',
    managerUserId: 6,
    managerName: '오세진',
    status: 'ACTIVE',
    memberCount: 2,
    level: 2,
  },
  {
    departmentId: 6,
    parentDepartmentId: 1,
    departmentName: '경영지원본부',
    managerUserId: 7,
    managerName: '최민호',
    status: 'ACTIVE',
    memberCount: 7,
    level: 1,
  },
  {
    departmentId: 7,
    parentDepartmentId: 6,
    departmentName: '인사팀',
    managerUserId: 4,
    managerName: '이서윤',
    status: 'ACTIVE',
    memberCount: 3,
    level: 2,
  },
  {
    departmentId: 8,
    parentDepartmentId: 6,
    departmentName: '재무팀',
    managerUserId: 7,
    managerName: '최민호',
    status: 'INACTIVE',
    memberCount: 2,
    level: 2,
  },
]

const managers = ['미지정', '이총괄', '김관리', '박준호', '오세진', '최민호', '이서윤']

function DepartmentManagementPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [selectedDepartment, setSelectedDepartment] = useState(departments[1])
  const [dialogMode, setDialogMode] = useState(null)
  const [feedback, setFeedback] = useState('')

  const filteredDepartments = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase()

    return departments.filter((department) => {
      const matchesSearch = [
        department.departmentName,
        department.managerName ?? '',
      ].some((value) => value.toLowerCase().includes(normalizedQuery))
      const matchesStatus =
        statusFilter === 'ALL' || department.status === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [searchQuery, statusFilter])

  const getParentName = (parentDepartmentId) =>
    departments.find(
      (department) => department.departmentId === parentDepartmentId,
    )?.departmentName ?? '최상위 부서'

  const childDepartments = departments.filter(
    (department) =>
      department.parentDepartmentId === selectedDepartment.departmentId,
  )

  const openDialog = (mode) => {
    setFeedback('')
    setDialogMode(mode)
  }

  const handleDialogSubmit = (event) => {
    event.preventDefault()
    setFeedback(
      dialogMode === 'create'
        ? '입력 확인이 완료되었습니다. 실제 생성은 API 연결 후 처리됩니다.'
        : '변경 내용 확인이 완료되었습니다. 실제 수정은 API 연결 후 처리됩니다.',
    )
  }

  return (
    <div className="admin-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">ADMIN · ORGANIZATION</span>
          <h1>부서 관리</h1>
          <p>계층형 조직 구조와 부서장, 운영 상태를 관리합니다.</p>
        </div>
        <button
          className="admin-primary-button"
          onClick={() => openDialog('create')}
          type="button"
        >
          <span aria-hidden="true">＋</span>
          부서 생성
        </button>
      </header>

      <section className="organization-summary">
        <div>
          <span>전체 부서</span>
          <strong>{departments.length}</strong>
        </div>
        <div>
          <span>운영 중</span>
          <strong>
            {
              departments.filter((department) => department.status === 'ACTIVE')
                .length
            }
          </strong>
        </div>
        <div>
          <span>부서장 미지정</span>
          <strong>
            {
              departments.filter(
                (department) => department.managerUserId === null,
              ).length
            }
          </strong>
        </div>
        <p>
          부서명은 트리 내에서 중복될 수 있으며, 고유 식별은 부서 ID를
          사용합니다.
        </p>
      </section>

      <div className="department-management-grid">
        <section className="panel department-tree-panel">
          <div className="admin-toolbar">
            <label className="approval-search">
              <span aria-hidden="true">⌕</span>
              <input
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="부서명 또는 부서장 검색"
                type="search"
                value={searchQuery}
              />
            </label>
            <select
              aria-label="부서 상태 필터"
              className="management-filter"
              onChange={(event) => setStatusFilter(event.target.value)}
              value={statusFilter}
            >
              <option value="ALL">전체 상태</option>
              <option value="ACTIVE">운영 중</option>
              <option value="INACTIVE">비활성</option>
            </select>
          </div>

          <div className="department-tree-heading">
            <span>조직 구조</span>
            <span>부서장</span>
            <span>인원</span>
            <span>상태</span>
          </div>

          <div className="department-tree">
            {filteredDepartments.map((department) => (
              <button
                className={`department-tree-row ${
                  selectedDepartment.departmentId === department.departmentId
                    ? 'department-tree-row--selected'
                    : ''
                }`}
                key={department.departmentId}
                onClick={() => setSelectedDepartment(department)}
                type="button"
              >
                <span
                  className="department-tree-name"
                  style={{ '--tree-level': department.level }}
                >
                  {department.level > 0 && (
                    <span className="tree-connector" aria-hidden="true">
                      └
                    </span>
                  )}
                  <span className="department-folder" aria-hidden="true">
                    {department.level < 2 ? '▣' : '□'}
                  </span>
                  <span>
                    <strong>{department.departmentName}</strong>
                    <small>ID {department.departmentId}</small>
                  </span>
                </span>
                <span>{department.managerName ?? '미지정'}</span>
                <span>{department.memberCount}명</span>
                <span
                  className={`user-status user-status--${department.status.toLowerCase()}`}
                >
                  {department.status === 'ACTIVE' ? '운영 중' : '비활성'}
                </span>
              </button>
            ))}
          </div>
        </section>

        <aside className="panel department-detail-panel">
          <header>
            <div>
              <span className="section-kicker">DEPARTMENT DETAIL</span>
              <h2>{selectedDepartment.departmentName}</h2>
              <p>부서 ID · {selectedDepartment.departmentId}</p>
            </div>
            <button
              className="management-edit-button"
              onClick={() => openDialog('edit')}
              type="button"
            >
              수정
            </button>
          </header>

          <dl className="department-detail-list">
            <div>
              <dt>상위 부서</dt>
              <dd>
                {getParentName(selectedDepartment.parentDepartmentId)}
              </dd>
            </div>
            <div>
              <dt>부서장</dt>
              <dd>{selectedDepartment.managerName ?? '미지정'}</dd>
            </div>
            <div>
              <dt>소속 인원</dt>
              <dd>{selectedDepartment.memberCount}명</dd>
            </div>
            <div>
              <dt>운영 상태</dt>
              <dd>
                {selectedDepartment.status === 'ACTIVE' ? '운영 중' : '비활성'}
              </dd>
            </div>
          </dl>

          <section className="department-child-section">
            <header>
              <h3>하위 부서</h3>
              <span>{childDepartments.length}</span>
            </header>
            {childDepartments.length > 0 ? (
              <ul>
                {childDepartments.map((department) => (
                  <li key={department.departmentId}>
                    <span>{department.departmentName}</span>
                    <small>{department.memberCount}명</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>등록된 하위 부서가 없습니다.</p>
            )}
          </section>

          <footer className="department-detail-actions">
            <button type="button">부서장 변경</button>
            <button className="danger-outline-button" type="button">
              비활성 처리
            </button>
          </footer>
        </aside>
      </div>

      {dialogMode && (
        <div className="decision-dialog-backdrop" role="presentation">
          <form
            aria-labelledby="department-dialog-title"
            className="decision-dialog management-dialog"
            onSubmit={handleDialogSubmit}
          >
            <header className="create-user-dialog__header">
              <div>
                <span className="section-kicker">
                  {dialogMode === 'create' ? 'NEW DEPARTMENT' : 'EDIT DEPARTMENT'}
                </span>
                <h2 id="department-dialog-title">
                  {dialogMode === 'create' ? '부서 생성' : '부서 정보 수정'}
                </h2>
                <p>상위 부서를 선택하여 조직 계층을 구성합니다.</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setDialogMode(null)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="management-form">
              <label className="form-field">
                <span>부서명</span>
                <input
                  defaultValue={
                    dialogMode === 'edit'
                      ? selectedDepartment.departmentName
                      : ''
                  }
                  placeholder="부서명을 입력하세요."
                  required
                />
              </label>
              <label className="form-field">
                <span>상위 부서</span>
                <select
                  defaultValue={
                    dialogMode === 'edit'
                      ? (selectedDepartment.parentDepartmentId ?? '')
                      : ''
                  }
                >
                  <option value="">최상위 부서</option>
                  {departments.map((department) => (
                    <option
                      key={department.departmentId}
                      value={department.departmentId}
                    >
                      {department.departmentName}
                    </option>
                  ))}
                </select>
              </label>
              <label className="form-field">
                <span>부서장</span>
                <select
                  defaultValue={
                    dialogMode === 'edit'
                      ? (selectedDepartment.managerName ?? '미지정')
                      : '미지정'
                  }
                >
                  {managers.map((manager) => (
                    <option key={manager} value={manager}>
                      {manager}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <p aria-live="polite" className="create-user-feedback">
              {feedback}
            </p>

            <footer className="decision-dialog__actions">
              <button onClick={() => setDialogMode(null)} type="button">
                취소
              </button>
              <button className="dialog-confirm" type="submit">
                {dialogMode === 'create' ? '생성하기' : '저장하기'}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  )
}

export default DepartmentManagementPage
