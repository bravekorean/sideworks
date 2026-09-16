import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAttendanceManagementScope, getManagedAttendances } from '../api/attendanceManagementApi'

const labels = {
  NOT_STARTED: '출근 전', WORKING: '근무 중', COMPLETED: '퇴근 기록',
  ABSENT: '결근', DAY_OFF: '휴무일', LEAVE: '휴가',
}
const time = (value) => value ? value.slice(11, 16) : '—'

function AttendanceManagementPage() {
  const [scope, setScope] = useState(null)
  const [filters, setFilters] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    getAttendanceManagementScope().then((data) => {
      if (!active) return
      setScope(data)
      if (!data.allDepartments && !data.departments.length) {
        setError('직원 근태 조회 권한이 없습니다.')
        setLoading(false)
        return
      }
      setFilters({ date: data.today, departmentId: '', name: '', page: 0, size: 20 })
    }).catch((failure) => {
      if (active) {
        setError(failure.response?.data?.message ?? '조회 권한 정보를 불러오지 못했습니다.')
        setLoading(false)
      }
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!filters) return
    let active = true
    getManagedAttendances({ ...filters, departmentId: filters.departmentId || undefined })
      .then((data) => { if (active) setResult(data) })
      .catch((failure) => {
        if (active) setError(failure.response?.status === 403
          ? '현재 권한으로 조회할 수 없는 범위입니다.'
          : failure.response?.data?.message ?? '근태 목록을 불러오지 못했습니다.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filters])

  const changeQuery = (next) => {
    setLoading(true)
    setError('')
    setResult(null)
    setFilters(next)
  }
  const submit = (event) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    changeQuery({ date: data.get('date'), departmentId: data.get('departmentId'),
      name: data.get('name').trim(), page: 0, size: 20 })
  }
  const allowed = scope && (scope.allDepartments || scope.departments.length > 0)

  return (
    <div className="admin-page">
      <header className="page-header"><div>
        <span className="section-kicker">PEOPLE · ATTENDANCE</span>
        <h1>직원 근태 조회</h1>
        <p>{scope?.allDepartments ? '전체 직원' : '담당 부서와 하위 부서'}의 출퇴근 기록을 확인합니다.</p>
      </div></header>
      {allowed && <form className="panel attendance-management-filters" onSubmit={submit}>
        <label className="form-field"><span>조회 날짜</span><input type="date" name="date" required defaultValue={scope.today} max={scope.today} /></label>
        <label className="form-field"><span>부서</span><select name="departmentId" defaultValue="">
          <option value="">조회 가능한 전체 부서</option>
          {scope.departments.map((department) => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}
        </select></label>
        <label className="form-field"><span>직원 이름</span><input name="name" maxLength={100} placeholder="이름 검색" /></label>
        <button className="admin-primary-button" disabled={loading} type="submit">조회</button>
      </form>}
      {error && <p role="alert" className="attendance-feedback attendance-feedback--error">{error}</p>}
      <section className="panel admin-table-panel" aria-busy={loading}>
        <div className="approval-table-wrapper"><table className="approval-table">
          <thead><tr><th>직원</th><th>부서 / 직급</th><th>날짜</th><th>출근</th><th>퇴근</th><th>상태</th><th>판정</th></tr></thead>
          <tbody>{loading ? <tr><td colSpan={7} className="approval-empty-state">불러오는 중입니다.</td></tr>
            : result?.content.length ? result.content.map((row) => <tr key={row.userId}>
              <td><strong>{row.userName}</strong><br /><small>{row.employeeNo}</small>
                {scope.allDepartments && <><br /><Link to={`/attendance-corrections?userId=${row.userId}&date=${row.attendanceDate}`}>기록 · 이력</Link></>}
              </td>
              <td>{row.departmentName ?? '부서 미배정'}<br /><small>{row.positionName ?? '직급 미배정'}</small></td>
              <td>{row.attendanceDate}</td><td>{time(row.checkInAt)}</td><td>{time(row.checkOutAt)}</td>
              <td><span className={`attendance-state attendance-state--${row.workState.toLowerCase()}`}>{labels[row.workState]}</span></td>
              <td>{row.late && <span className="attendance-flag attendance-flag--late">지각</span>} {row.earlyLeave && <span className="attendance-flag attendance-flag--early">조퇴</span>}{!row.late && !row.earlyLeave && '—'}</td>
            </tr>) : <tr><td colSpan={7} className="approval-empty-state">{error ? '조회할 수 없습니다.' : '조건에 맞는 직원이 없습니다.'}</td></tr>}</tbody>
        </table></div>
        {result && <footer className="admin-table-footer">
          <span>총 {result.totalElements}명 · {result.totalPages ? result.page + 1 : 0} / {result.totalPages} 페이지</span>
          <div className="attendance-month-navigation">
            <button type="button" disabled={loading || result.first} onClick={() => changeQuery({ ...filters, page: filters.page - 1 })}>이전</button>
            <button type="button" disabled={loading || result.last} onClick={() => changeQuery({ ...filters, page: filters.page + 1 })}>다음</button>
          </div>
        </footer>}
      </section>
      <p className="attendance-management-note">현재 재직자와 현재 소속 부서 기준이며, 입사일 이전 직원은 제외됩니다. 부서 선택 시 해당 부서의 직속 직원만 표시합니다. 날짜별 휴무·근무 설정을 우선 적용하며 휴가 연동은 아직 적용되지 않았습니다.</p>
    </div>
  )
}

export default AttendanceManagementPage
