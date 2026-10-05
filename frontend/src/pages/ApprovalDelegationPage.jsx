import { useEffect, useState } from 'react'
import { cancelDelegation, createDelegation, getDelegations } from '../api/approvalDelegationApi'
import { getDirectory, getMyProfile } from '../api/userApi'

export default function ApprovalDelegationPage() {
  const [profile, setProfile] = useState(null)
  const [users, setUsers] = useState([])
  const [delegatorId, setDelegatorId] = useState('')
  const [delegateeId, setDelegateeId] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [entries, setEntries] = useState([])
  const [feedback, setFeedback] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([getMyProfile(), getDirectory(0, 100)])
      .then(([me, directory]) => {
        if (!active) return
        setProfile(me)
        setUsers(directory.content)
        setDelegatorId(String(me.userId))
      })
      .catch((error) => {
        if (active) setFeedback(error.response?.data?.message ?? '사용자 정보를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!delegatorId) return undefined
    let active = true
    getDelegations(Number(delegatorId))
      .then((data) => { if (active) setEntries(data) })
      .catch((error) => { if (active) setFeedback(error.response?.data?.message ?? '위임 목록을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [delegatorId, reload])

  const submit = async (event) => {
    event.preventDefault()
    if (!delegateeId || !startDate || !endDate) {
      setFeedback('대리인과 시작일·종료일을 선택해주세요.')
      return
    }
    setBusy(true)
    setFeedback('')
    try {
      await createDelegation({ delegatorId: Number(delegatorId), delegateeId: Number(delegateeId), startDate, endDate })
      setFeedback('결재 위임을 등록했습니다.')
      setReload((value) => value + 1)
    } catch (error) {
      setFeedback(error.response?.data?.message ?? '결재 위임을 등록하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const cancel = async (entry) => {
    if (!window.confirm(`${entry.delegateeName}님에게 설정한 위임을 취소할까요? 이미 배정된 결재는 유지됩니다.`)) return
    setBusy(true)
    setFeedback('')
    try {
      await cancelDelegation(entry.approvalDelegationId)
      setFeedback('위임을 취소했습니다. 이미 배정된 결재는 기존 대리인이 처리합니다.')
      setReload((value) => value + 1)
    } catch (error) {
      setFeedback(error.response?.data?.message ?? '위임을 취소하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return <div className="delegation-page">
    <header><span className="section-kicker">APPROVAL · DELEGATION</span><h1>기간제 결재 위임</h1>
      <p>위임 기간에 새로 도착하는 결재만 대리인에게 배정됩니다. 기존 대기 문서는 변경되지 않습니다.</p></header>
    {feedback && <p aria-live="polite" className="detail-feedback">{feedback}</p>}
    <section className="panel delegation-panel">
      <h2>위임 등록</h2>
      <form className="delegation-form" onSubmit={submit}>
        {profile?.userRole === 'SUPER_ADMIN' && <label>원래 결재자
          <select value={delegatorId} onChange={(event) => setDelegatorId(event.target.value)} required>
            {users.map((user) => <option key={user.userId} value={user.userId}>{user.userName} · {user.departmentName || '부서 미배정'}</option>)}
          </select>
        </label>}
        <label>대리인
          <select value={delegateeId} onChange={(event) => setDelegateeId(event.target.value)} required>
            <option value="">선택해주세요</option>
            {users.filter((user) => String(user.userId) !== delegatorId).map((user) =>
              <option key={user.userId} value={user.userId}>{user.userName} · {user.departmentName || '부서 미배정'}</option>)}
          </select>
        </label>
        <label>시작일<input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} required /></label>
        <label>종료일<input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} required /></label>
        <button type="submit" disabled={busy}>위임 등록</button>
      </form>
    </section>
    <section className="panel delegation-panel">
      <h2>위임 이력</h2>
      {entries.length === 0 ? <p>등록된 위임이 없습니다.</p> : <div className="delegation-list">
        {entries.map((entry) => <div className="delegation-item" key={entry.approvalDelegationId}>
          <div><strong>{entry.delegatorName} → {entry.delegateeName}</strong>
            <small>{entry.startDate} ~ {entry.endDate} · {entry.canceledAt ? '취소됨' : '등록됨'}</small></div>
          {!entry.canceledAt && <button type="button" disabled={busy} onClick={() => cancel(entry)}>취소</button>}
        </div>)}
      </div>}
    </section>
  </div>
}
