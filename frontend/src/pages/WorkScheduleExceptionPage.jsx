import { useEffect, useState } from 'react'
import { getScheduleExceptions, createScheduleException, updateScheduleException } from '../api/attendanceCorrectionApi'

const errorMessage = (error) => error.response?.data?.message ?? '날짜별 근무 설정을 처리하지 못했습니다.'

function WorkScheduleExceptionPage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [rows, setRows] = useState([])
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(true)
  const [feedback, setFeedback] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    getScheduleExceptions(year).then((data) => { if (active) setRows(data) })
      .catch((error) => { if (active) setFeedback(errorMessage(error)) })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [year, reload])

  const save = async (event) => {
    event.preventDefault()
    const formElement = event.currentTarget
    const data = new FormData(formElement)
    const payload = {
      date: data.get('date'), name: data.get('name'), working: data.get('working') === 'true',
      active: data.get('active') === 'true', version: editing?.version,
    }
    if (!window.confirm('기존 출퇴근 기록은 유지되며, 기록 없는 날짜의 근태 판정이 변경됩니다. 저장할까요?')) return
    setBusy(true); setFeedback('')
    try {
      if (editing) await updateScheduleException(editing.id, payload)
      else await createScheduleException(payload)
      setEditing(null); formElement.reset(); setFeedback('저장했습니다.')
      setYear(Number(payload.date.slice(0, 4))); setReload((value) => value + 1)
    } catch (error) { setFeedback(errorMessage(error)); setBusy(false) }
  }
  return <div className="admin-page">
    <header className="page-header"><div><span className="section-kicker">PEOPLE · WORK SCHEDULE</span>
      <h1>휴무일 · 예외 근무일 관리</h1><p>공휴일과 회사 휴무일, 특정 날짜의 주말 근무를 관리합니다.</p>
    </div></header>
    {feedback && <p className="attendance-feedback" role="status">{feedback}</p>}
    <form className="panel attendance-correction-panel attendance-correction-form" key={editing ? `${editing.id}-${editing.version}` : 'new'} onSubmit={save}>
      <h2>{editing ? '날짜 설정 수정' : '날짜 설정 등록'}</h2>
      <label className="form-field"><span>날짜</span><input type="date" name="date" required readOnly={Boolean(editing)} min="1900-01-01" max="2100-12-31" defaultValue={editing?.date || ''} /></label>
      <label className="form-field"><span>명칭</span><input name="name" required maxLength={100} defaultValue={editing?.name || ''} placeholder="예: 창립기념일, 임시 주말근무" /></label>
      <label className="form-field"><span>근무 여부</span><select name="working" defaultValue={String(editing?.working ?? false)}><option value="false">휴무일</option><option value="true">근무일 (회사 기본 근무시간 적용)</option></select></label>
      <label className="form-field"><span>적용 상태</span><select name="active" defaultValue={String(editing?.active ?? true)} disabled={!editing}><option value="true">적용</option><option value="false">해제 (요일 정책으로 복귀)</option></select></label>
      <div><button className="admin-primary-button" disabled={busy}>저장</button> {editing && <button type="button" disabled={busy} onClick={() => setEditing(null)}>새로 등록</button>}</div>
    </form>
    <section className="panel attendance-correction-panel">
      <label className="form-field"><span>조회 연도</span><input type="number" value={year} min={1900} max={2100} disabled={busy} onChange={(event) => {
        const next = Number(event.target.value)
        if (next >= 1900 && next <= 2100) { setBusy(true); setRows([]); setFeedback(''); setYear(next) }
      }} /></label>
      <div className="approval-table-wrapper"><table className="approval-table"><thead><tr><th>날짜</th><th>명칭</th><th>설정</th><th>상태</th><th>관리</th></tr></thead><tbody>
        {rows.map((row) => <tr key={row.id}><td>{row.date}</td><td>{row.name}</td><td>{row.working ? '근무일' : '휴무일'}</td><td>{row.active ? '적용 중' : '해제됨'}</td><td><button disabled={busy} type="button" onClick={() => setEditing(row)}>수정</button></td></tr>)}
        {!rows.length && <tr><td colSpan={5}>{busy ? '불러오는 중…' : '등록된 날짜 설정이 없습니다.'}</td></tr>}
      </tbody></table></div>
    </section>
    <p className="attendance-management-note">날짜별 설정이 요일별 근무 여부보다 우선합니다. 해제한 날짜는 수정하여 재활성화할 수 있습니다. 기존 출퇴근 기록은 자동 수정하지 않습니다. 외부 공휴일 API 가져오기는 아직 연결하지 않았습니다.</p>
  </div>
}
export default WorkScheduleExceptionPage
