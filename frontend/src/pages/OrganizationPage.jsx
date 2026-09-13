import { useEffect, useMemo, useState } from 'react'
import {
  getDepartmentMembers,
  getOrganizationDepartments,
} from '../api/organizationApi'

function orderAsTree(departments) {
  const childrenByParent = new Map()
  const ids = new Set(departments.map(({ departmentId }) => departmentId))

  departments.forEach((department) => {
    const parentId = ids.has(department.parentDepartmentId)
      ? department.parentDepartmentId
      : null
    const children = childrenByParent.get(parentId) ?? []
    children.push(department)
    childrenByParent.set(parentId, children)
  })

  const result = []
  const visited = new Set()
  const append = (department, level) => {
    if (visited.has(department.departmentId)) return
    visited.add(department.departmentId)
    result.push({ ...department, level })
    ;(childrenByParent.get(department.departmentId) ?? []).forEach((child) =>
      append(child, level + 1),
    )
  }

  ;(childrenByParent.get(null) ?? []).forEach((department) => append(department, 0))
  departments.forEach((department) => append(department, 0))
  return result
}

function OrganizationPage() {
  const [departments, setDepartments] = useState([])
  const [selectedDepartmentId, setSelectedDepartmentId] = useState(null)
  const [memberPage, setMemberPage] = useState(null)
  const [selectedMember, setSelectedMember] = useState(null)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [membersLoading, setMembersLoading] = useState(false)
  const [feedback, setFeedback] = useState('')

  useEffect(() => {
    let active = true

    getOrganizationDepartments()
      .then((data) => {
        if (!active) return
        setDepartments(data)
        setMembersLoading(data.length > 0)
        setSelectedDepartmentId(data[0]?.departmentId ?? null)
      })
      .catch((error) => {
        if (active) {
          setFeedback(error.response?.data?.message ?? '조직도를 불러오지 못했습니다.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!selectedDepartmentId) return
    let active = true

    getDepartmentMembers(selectedDepartmentId, page)
      .then((data) => {
        if (!active) return
        setMemberPage(data)
        setSelectedMember((current) =>
          data.content.find(({ userId }) => userId === current?.userId) ??
          data.content[0] ??
          null,
        )
      })
      .catch((error) => {
        if (active) {
          setFeedback(error.response?.data?.message ?? '구성원을 불러오지 못했습니다.')
          setMemberPage(null)
          setSelectedMember(null)
        }
      })
      .finally(() => {
        if (active) setMembersLoading(false)
      })

    return () => {
      active = false
    }
  }, [selectedDepartmentId, page])

  const tree = useMemo(() => orderAsTree(departments), [departments])
  const filteredTree = tree.filter(({ departmentName, managerName }) =>
    `${departmentName} ${managerName ?? ''}`.toLowerCase().includes(query.trim().toLowerCase()),
  )
  const selectedDepartment = departments.find(
    ({ departmentId }) => departmentId === selectedDepartmentId,
  )

  const selectDepartment = (departmentId) => {
    setMembersLoading(true)
    setFeedback('')
    setSelectedDepartmentId(departmentId)
    setSelectedMember(null)
    setPage(0)
  }

  const changePage = (nextPage) => {
    setMembersLoading(true)
    setFeedback('')
    setPage(nextPage)
  }

  return (
    <div className="organization-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">WORKSPACE · ORGANIZATION</span>
          <h1>조직도</h1>
          <p>부서 구조와 직속 구성원을 확인합니다.</p>
        </div>
      </header>

      {feedback && <div className="organization-feedback">{feedback}</div>}

      <section className="organization-browser">
        <aside className="organization-tree-panel">
          <header><strong>부서</strong><span>{departments.length}</span></header>
          <input
            aria-label="부서 검색"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="부서 또는 부서장 검색"
            type="search"
            value={query}
          />
          <div className="organization-tree">
            {filteredTree.map((department) => (
              <button
                className={department.departmentId === selectedDepartmentId ? 'is-selected' : ''}
                key={department.departmentId}
                onClick={() => selectDepartment(department.departmentId)}
                style={{ paddingLeft: `${14 + department.level * 18}px` }}
                type="button"
              >
                <span>{department.level > 0 ? '└' : '▣'}</span>
                <span><strong>{department.departmentName}</strong><small>{department.memberCount}명</small></span>
              </button>
            ))}
          </div>
        </aside>

        <div className="organization-members-panel">
          <header>
            <div><span>선택 부서</span><h2>{selectedDepartment?.departmentName ?? '부서를 선택하세요'}</h2></div>
            <div><span>부서장</span><strong>{selectedDepartment?.managerName ?? '미지정'}</strong></div>
          </header>
          <div className="organization-member-list">
            {(loading || membersLoading) && <p className="organization-empty">불러오는 중...</p>}
            {!loading && !membersLoading && memberPage?.content.length === 0 && <p className="organization-empty">소속 구성원이 없습니다.</p>}
            {!loading && !membersLoading && memberPage?.content.map((member) => (
              <button
                className={member.userId === selectedMember?.userId ? 'is-selected' : ''}
                key={member.userId}
                onClick={() => setSelectedMember(member)}
                type="button"
              >
                <span className="organization-avatar">{member.userName.slice(0, 2)}</span>
                <span><strong>{member.userName}{member.manager && <em>부서장</em>}</strong><small>{member.positionName ?? '직급 미지정'} · {member.employeeNo}</small></span>
              </button>
            ))}
          </div>
          {memberPage && memberPage.totalPages > 1 && (
            <footer className="organization-pagination">
              <button disabled={memberPage.first} onClick={() => changePage(page - 1)} type="button">이전</button>
              <span>{memberPage.page + 1} / {memberPage.totalPages}</span>
              <button disabled={memberPage.last} onClick={() => changePage(page + 1)} type="button">다음</button>
            </footer>
          )}
        </div>

        <aside className="organization-detail-panel">
          <span className="section-kicker">MEMBER DETAIL</span>
          {selectedMember ? (
            <div className="organization-member-detail">
              <span className="organization-avatar organization-avatar--large">{selectedMember.userName.slice(0, 2)}</span>
              <h2>{selectedMember.userName}</h2>
              {selectedMember.manager && <span className="organization-manager-badge">부서장</span>}
              <dl>
                <div><dt>부서</dt><dd>{selectedDepartment?.departmentName}</dd></div>
                <div><dt>직급</dt><dd>{selectedMember.positionName ?? '미지정'}</dd></div>
                <div><dt>사번</dt><dd>{selectedMember.employeeNo}</dd></div>
              </dl>
            </div>
          ) : <p className="organization-empty">구성원을 선택하세요.</p>}
        </aside>
      </section>
    </div>
  )
}

export default OrganizationPage
