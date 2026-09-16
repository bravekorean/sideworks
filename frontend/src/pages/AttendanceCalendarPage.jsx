import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import {
  checkIn,
  checkOut,
  getMonthlyAttendances,
  getTodayAttendance,
} from '../api/attendanceApi'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

const STATE_LABELS = {
  NOT_STARTED: '출근 전',
  WORKING: '근무 중',
  COMPLETED: '퇴근 기록',
  ABSENT: '결근',
  DAY_OFF: '휴무일',
  LEAVE: '휴가',
}

function toDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function createCalendarDays(year, month) {
  const firstDay = new Date(year, month - 1, 1)
  const firstCell = new Date(year, month - 1, 1 - firstDay.getDay())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(firstCell)
    date.setDate(firstCell.getDate() + index)

    return {
      date,
      dateKey: toDateKey(date),
      currentMonth: date.getMonth() === month - 1,
    }
  })
}

function formatTime(value) {
  return value ? value.slice(11, 16) : '--:--'
}

function formatSelectedDate(dateKey) {
  if (!dateKey) return ''

  const [year, month, day] = dateKey.split('-').map(Number)
  const date = new Date(year, month - 1, day)

  return `${year}년 ${month}월 ${day}일 ${WEEKDAYS[date.getDay()]}요일`
}

function calculateWorkDuration(checkInAt, checkOutAt) {
  if (!checkInAt || !checkOutAt) return '--'

  const minutes = Math.max(
    0,
    Math.floor((new Date(checkOutAt).getTime() - new Date(checkInAt).getTime()) / 60000),
  )
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60

  return `${hours}시간 ${remainingMinutes}분`
}

function getErrorMessage(error, fallback) {
  return error.response?.data?.message ?? fallback
}

function AttendanceCalendarPage() {
  const now = new Date()
  const [viewDate, setViewDate] = useState(
    () => new Date(now.getFullYear(), now.getMonth(), 1),
  )
  const [todayAttendance, setTodayAttendance] = useState(null)
  const [monthlyAttendances, setMonthlyAttendances] = useState([])
  const [todayLoading, setTodayLoading] = useState(true)
  const [monthLoading, setMonthLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [feedbackType, setFeedbackType] = useState('error')
  const [selectedDateKey, setSelectedDateKey] = useState(() => toDateKey(now))

  const viewYear = viewDate.getFullYear()
  const viewMonth = viewDate.getMonth() + 1
  const todayKey = toDateKey(now)

  useEffect(() => {
    let active = true

    getTodayAttendance()
      .then((data) => {
        if (active) setTodayAttendance(data)
      })
      .catch((error) => {
        if (active) {
          setFeedback(getErrorMessage(error, '오늘 근태 정보를 불러오지 못했습니다.'))
          setFeedbackType('error')
        }
      })
      .finally(() => {
        if (active) setTodayLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    getMonthlyAttendances(viewYear, viewMonth)
      .then((data) => {
        if (active) setMonthlyAttendances(data)
      })
      .catch((error) => {
        if (active) {
          setFeedback(getErrorMessage(error, '월별 근태 기록을 불러오지 못했습니다.'))
          setFeedbackType('error')
        }
      })
      .finally(() => {
        if (active) setMonthLoading(false)
      })

    return () => {
      active = false
    }
  }, [viewMonth, viewYear])

  const recordsByDate = useMemo(
    () => new Map(monthlyAttendances.map((record) => [record.attendanceDate, record])),
    [monthlyAttendances],
  )
  const calendarDays = useMemo(
    () => createCalendarDays(viewYear, viewMonth),
    [viewMonth, viewYear],
  )
  const selectedAttendance = selectedDateKey === todayKey
    ? todayAttendance ?? recordsByDate.get(selectedDateKey)
    : recordsByDate.get(selectedDateKey)
  const monthlySummary = useMemo(() => ({
    recorded: monthlyAttendances.filter((record) => record.attendanceId !== null).length,
    completed: monthlyAttendances.filter((record) => record.workState === 'COMPLETED').length,
    late: monthlyAttendances.filter((record) => record.late).length,
    earlyLeave: monthlyAttendances.filter((record) => record.earlyLeave).length,
  }), [monthlyAttendances])

  const changeMonth = (amount) => {
    setMonthLoading(true)
    setViewDate((current) => {
      const next = new Date(current.getFullYear(), current.getMonth() + amount, 1)
      setSelectedDateKey(toDateKey(next))
      return next
    })
  }

  const moveToCurrentMonth = () => {
    const current = new Date()
    setMonthLoading(true)
    setViewDate(new Date(current.getFullYear(), current.getMonth(), 1))
    setSelectedDateKey(toDateKey(current))
  }

  const refreshVisibleMonth = async () => {
    const data = await getMonthlyAttendances(viewYear, viewMonth)
    setMonthlyAttendances(data)
  }

  const handleAttendanceAction = async (action) => {
    setActionLoading(true)
    setFeedback('')

    try {
      const response = action === 'check-in' ? await checkIn() : await checkOut()
      setTodayAttendance(response)
      await refreshVisibleMonth()
      setFeedback(action === 'check-in' ? '출근 처리되었습니다.' : '퇴근 처리되었습니다.')
      setFeedbackType('success')
    } catch (error) {
      setFeedback(getErrorMessage(error, '근태 처리 중 오류가 발생했습니다.'))
      setFeedbackType('error')
    } finally {
      setActionLoading(false)
    }
  }

  const workState = todayAttendance?.workState
  const canCheckIn = todayAttendance?.checkInAllowed === true
  const canCheckOut = todayAttendance?.checkOutAllowed === true

  return (
    <div className="attendance-page">
      <header className="page-header attendance-page__header">
        <div>
          <span className="section-kicker">WORKSPACE · ATTENDANCE</span>
          <h1>근태 캘린더</h1>
          <p>출퇴근을 기록하고 월별 근태 현황을 확인합니다.</p>
          <Link to="/approvals/new?type=attendance-correction">근태 정정 결재 작성</Link>
          {' · '}<Link to="/attendance-corrections">변경 이력</Link>
        </div>
      </header>

      {feedback && (
        <div className={`attendance-feedback attendance-feedback--${feedbackType}`}>
          {feedback}
        </div>
      )}

      <section className="attendance-today-card">
        <div className="attendance-today-card__status">
          <span>오늘의 근태</span>
          <strong>
            {todayLoading
              ? '불러오는 중'
              : STATE_LABELS[workState] ?? '확인 필요'}
          </strong>
          <small>{todayAttendance?.attendanceDate ?? todayKey}</small>
        </div>

        <dl className="attendance-time-summary">
          <div>
            <dt>출근</dt>
            <dd>{formatTime(todayAttendance?.checkInAt)}</dd>
          </div>
          <div>
            <dt>퇴근</dt>
            <dd>{formatTime(todayAttendance?.checkOutAt)}</dd>
          </div>
          <div>
            <dt>판정</dt>
            <dd>
              {todayLoading && '--'}
              {!todayLoading && !todayAttendance && '기록 없음'}
              {!todayLoading && todayAttendance?.late && <span className="attendance-flag attendance-flag--late">지각</span>}
              {!todayLoading && todayAttendance?.earlyLeave && <span className="attendance-flag attendance-flag--early">조퇴</span>}
              {!todayLoading && todayAttendance && !todayAttendance.late && !todayAttendance.earlyLeave && '정상'}
            </dd>
          </div>
        </dl>

        <div className="attendance-actions">
          <button
            className="attendance-action attendance-action--check-in"
            disabled={!canCheckIn || actionLoading}
            onClick={() => handleAttendanceAction('check-in')}
            type="button"
          >
            {actionLoading && canCheckIn ? '처리 중...' : '출근하기'}
          </button>
          <button
            className="attendance-action attendance-action--check-out"
            disabled={!canCheckOut || actionLoading}
            onClick={() => handleAttendanceAction('check-out')}
            type="button"
          >
            {actionLoading && canCheckOut ? '처리 중...' : '퇴근하기'}
          </button>
        </div>
      </section>

      <section className="attendance-workspace">
        <div className="attendance-calendar-panel">
          <header className="attendance-calendar-toolbar">
            <div>
              <span>MONTHLY RECORD</span>
              <h2>{viewYear}년 {viewMonth}월</h2>
            </div>
            <div className="attendance-calendar-toolbar__controls">
              <span className="attendance-view-switch" aria-label="캘린더 보기 방식">
                <button className="is-active" type="button">월간</button>
                <button disabled title="주간 보기는 추후 제공됩니다" type="button">주간</button>
              </span>
              <span className="attendance-month-navigation">
                <button aria-label="이전 달" onClick={() => changeMonth(-1)} type="button">‹</button>
                <button onClick={moveToCurrentMonth} type="button">오늘</button>
                <button aria-label="다음 달" onClick={() => changeMonth(1)} type="button">›</button>
              </span>
            </div>
          </header>

          <div className="attendance-month-summary">
            <span><small>기록일</small><strong>{monthlySummary.recorded}</strong>일</span>
            <span><small>퇴근 완료</small><strong>{monthlySummary.completed}</strong>일</span>
            <span><small>지각</small><strong>{monthlySummary.late}</strong>회</span>
            <span><small>조퇴</small><strong>{monthlySummary.earlyLeave}</strong>회</span>
          </div>

          <div className="attendance-calendar-weekdays">
            {WEEKDAYS.map((weekday) => <span key={weekday}>{weekday}</span>)}
          </div>

          <div className={`attendance-calendar-grid ${monthLoading ? 'is-loading' : ''}`}>
            {calendarDays.map(({ date, dateKey, currentMonth }) => {
              const record = dateKey === todayKey
                ? todayAttendance ?? recordsByDate.get(dateKey)
                : recordsByDate.get(dateKey)

              return (
                <button
                  aria-label={`${formatSelectedDate(dateKey)} 근태 상세 보기`}
                  className={`attendance-calendar-day ${currentMonth ? '' : 'is-outside'} ${dateKey === todayKey ? 'is-today' : ''} ${dateKey === selectedDateKey ? 'is-selected' : ''}`}
                  key={dateKey}
                  onClick={() => setSelectedDateKey(dateKey)}
                  type="button"
                >
                  <time dateTime={dateKey}>{date.getDate()}</time>
                  {record ? (
                    <div className="attendance-calendar-day__records">
                      <span className={`attendance-state attendance-state--${record.workState.toLowerCase()}`}>
                        {STATE_LABELS[record.workState]}
                      </span>
                      <small><b>{formatTime(record.checkInAt)}</b> 출근</small>
                      {record.checkOutAt && <small><b>{formatTime(record.checkOutAt)}</b> 퇴근</small>}
                      <div>
                        {record.late && <em>지각</em>}
                        {record.earlyLeave && <em>조퇴</em>}
                      </div>
                    </div>
                  ) : (
                    currentMonth && <span className="attendance-calendar-day__empty">기록 없음</span>
                  )}
                </button>
              )
            })}
          </div>
        </div>

        <aside className="attendance-day-detail">
          <div className="attendance-day-detail__heading">
            <span>SELECTED DATE</span>
            <h2>{formatSelectedDate(selectedDateKey)}</h2>
          </div>

          {selectedAttendance ? (
            <>
              <div className="attendance-day-detail__state">
                <span className={`attendance-state attendance-state--${selectedAttendance.workState.toLowerCase()}`}>
                  {STATE_LABELS[selectedAttendance.workState]}
                </span>
                <p>
                  {selectedAttendance.late && <em>지각</em>}
                  {selectedAttendance.earlyLeave && <em>조퇴</em>}
                  {!selectedAttendance.late && !selectedAttendance.earlyLeave && '특이사항 없음'}
                </p>
              </div>

              <dl className="attendance-day-detail__times">
                <div><dt>출근 시각</dt><dd>{formatTime(selectedAttendance.checkInAt)}</dd></div>
                <div><dt>퇴근 시각</dt><dd>{formatTime(selectedAttendance.checkOutAt)}</dd></div>
                <div>
                  <dt>출퇴근 기록 간격</dt>
                  <dd>{calculateWorkDuration(selectedAttendance.checkInAt, selectedAttendance.checkOutAt)}</dd>
                </div>
              </dl>
            </>
          ) : (
            <div className="attendance-day-detail__empty">
              <span aria-hidden="true">○</span>
              <strong>근태 기록이 없습니다</strong>
              <p>이 날짜에는 저장된 출퇴근 기록이 없습니다.</p>
            </div>
          )}

          <div className="attendance-day-detail__notice">
            <strong>근태 기록 안내</strong>
            <p>기록 변경은 근태 정정 결재 승인으로 반영됩니다.</p>
          </div>
        </aside>
      </section>
    </div>
  )
}

export default AttendanceCalendarPage
