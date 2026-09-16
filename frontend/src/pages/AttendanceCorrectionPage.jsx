import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getMyProfile } from '../api/userApi'
import {
  getCorrectionRecord, getCorrectionHistory, correctAttendanceDirectly,
} from '../api/attendanceCorrectionApi'

const show = (value) => value ? value.replace('T', ' ').slice(0, 19) : '미기록'
const message = (error) => error.response?.data?.message ?? '요청을 처리하지 못했습니다.'

function AttendanceCorrectionPage() {
  const [params] = useSearchParams()
  const userId = params.get('userId') || undefined
  const [date, setDate] = useState(params.get('date') || '')
  const [profile, setProfile] = useState(null)
  const [loaded, setLoaded] = useState(null)
  const [history, setHistory] = useState([])
  const [busy, setBusy] = useState(false)
  const [feedback, setFeedback] = useState('')
  const isOther = Boolean(userId && String(profile?.userId) !== userId)
  const [direct, setDirect] = useState(false)

  useEffect(() => {
    let active = true
    getMyProfile().then((me) => {
      if (active) setProfile(me)
    }).catch((error) => { if (active) setFeedback(message(error)) })
    return () => { active = false }
  }, [])

  const load = async (event) => {
    event.preventDefault()
    setBusy(true); setFeedback(''); setLoaded(null); setHistory([])
    try {
      const [record, changes] = await Promise.all([
        getCorrectionRecord({ date, userId }), getCorrectionHistory({ date, userId }),
      ])
      setLoaded({ ...record, date }); setHistory(changes)
    } catch (error) { setFeedback(message(error)) }
    finally { setBusy(false) }
  }

  const save = async (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const data = {
      date: loaded.date, checkInAt: form.get('checkInAt'), checkOutAt: form.get('checkOutAt') || null,
      reason: form.get('reason'), approverId: null,
      expectedAttendanceId: loaded.attendanceId, expectedVersion: loaded.version,
    }
    if (!window.confirm('결재 없이 근태를 직접 정정합니다. 사유와 변경 이력이 보존됩니다. 진행할까요?')) return
    setBusy(true); setFeedback('')
    try {
      await correctAttendanceDirectly(userId || profile.userId, data)
      setFeedback('직접 정정을 반영했습니다. 기록 조회를 다시 눌러 변경 이력을 확인해주세요.')
      setLoaded(null)
    } catch (error) { setFeedback(message(error)) }
    finally { setBusy(false) }
  }

  const canEdit = profile?.userRole === 'SUPER_ADMIN'
  return <div className="admin-page">
    <header className="page-header"><div><span className="section-kicker">ATTENDANCE · CORRECTION</span>
      <h1>근태 기록 · 변경 이력</h1><p>{isOther ? `대상 사용자 ID: ${userId}` : '본인의 출퇴근 기록과 변경 이력을 확인합니다.'}</p>
      {!isOther && <Link to={`/approvals/new?type=attendance-correction${date ? `&date=${date}` : ''}`}>근태 정정 결재 작성</Link>}
    </div></header>
    <form className="panel attendance-management-filters" onSubmit={load}>
      <label className="form-field"><span>대상 날짜</span><input type="date" required value={date} disabled={busy}
        onChange={(event) => { setDate(event.target.value); setLoaded(null); setHistory([]) }} /></label>
      <button className="admin-primary-button" disabled={busy || !profile}>기록 조회</button>
    </form>
    {feedback && <p role="status" className="attendance-feedback">{feedback}</p>}
    {loaded && <section className="panel attendance-correction-panel">
      <h2>{loaded.date} 현재 기록</h2><p>출근: {show(loaded.checkInAt)} / 퇴근: {show(loaded.checkOutAt)}</p>
      {canEdit && <label><input type="checkbox" checked={direct} disabled={busy}
        onChange={(event) => setDirect(event.target.checked)} /> 시스템 관리자 직접 정정 열기</label>}
      {canEdit && direct && <form key={`${loaded.date}-${loaded.version}`} className="attendance-correction-form" onSubmit={save}>
        <label className="form-field"><span>변경할 출근 시각</span><input type="datetime-local" name="checkInAt" required step="1" defaultValue={loaded.checkInAt?.slice(0, 19) || `${loaded.date}T09:00:00`} /></label>
        <label className="form-field"><span>변경할 퇴근 시각 (공란은 미기록)</span><input type="datetime-local" name="checkOutAt" step="1" defaultValue={loaded.checkOutAt?.slice(0, 19) || ''} /></label>
        <label className="form-field"><span>정정 사유</span><textarea name="reason" required maxLength={1000} rows={3} /></label>
        <button className="admin-primary-button" disabled={busy}>{busy ? '처리 중…' : '직접 정정 반영'}</button>
      </form>}
      {!canEdit && <p>근태 변경은 본인이 작성한 정정 결재의 승인으로 반영됩니다.</p>}
    </section>}
    <section className="panel attendance-correction-panel"><h2>변경 이력</h2>
      {history.length ? history.map((h) => <article className="attendance-history-entry" key={h.id}>
        <strong>{show(h.createdAt)} · {h.source === 'APPROVAL' ? '결재 승인' : '관리자 직접 정정'} · 처리자 ID {h.actorId}</strong>
        <p>출근 {show(h.beforeCheckInAt)} → {show(h.afterCheckInAt)}<br />퇴근 {show(h.beforeCheckOutAt)} → {show(h.afterCheckOutAt)}</p>
        <p>지각 {String(h.beforeLate ?? '기록 없음')} → {h.afterLate ? '예' : '아니오'} / 조퇴 {String(h.beforeEarlyLeave ?? '기록 없음')} → {h.afterEarlyLeave ? '예' : '아니오'}</p>
        <p className="attendance-reason">사유: {h.reason}</p>{h.approvalId && <span>연결 결재 #{h.approvalId}</span>}
      </article>) : <p>날짜를 조회하면 승인 또는 직접 정정된 이력이 표시됩니다.</p>}
    </section>
    <p className="attendance-management-note">같은 날짜의 시각만 입력할 수 있고 출근 시각은 필수입니다. 미래 시각은 허용하지 않습니다. 신청 후 원본이 변경되면 기존 신청을 취소하고 다시 신청해야 합니다.</p>
  </div>
}
export default AttendanceCorrectionPage
