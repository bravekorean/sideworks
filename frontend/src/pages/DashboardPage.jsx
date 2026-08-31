import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  getApprovalBox,
  getRecentApprovalActivities,
} from '../api/approvalApi'
import { getMyProfile } from '../api/userApi'

const emptySummary = {
  pending: 0,
  inProgress: 0,
  drafts: 0,
  cc: 0,
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

const activityPresentation = {
  SUBMITTED: { message: '문서를 상신했습니다.', tone: 'submitted' },
  APPROVED: { message: '결재를 승인했습니다.', tone: 'approved' },
  REJECTED: { message: '결재를 반려했습니다.', tone: 'rejected' },
  CANCELED: { message: '상신을 취소했습니다.', tone: 'pending' },
}

function DashboardPage() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [summary, setSummary] = useState(emptySummary)
  const [pendingApprovals, setPendingApprovals] = useState([])
  const [recentActivities, setRecentActivities] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let isActive = true

    const loadDashboard = async () => {
      try {
        const [profileResponse, pending, sent, drafts, cc, activities] = await Promise.all([
          getMyProfile(),
          getApprovalBox('pending', { page: 0, size: 4 }),
          getApprovalBox('sent', {
            page: 0,
            size: 1,
            status: 'IN_PROGRESS',
          }),
          getApprovalBox('drafts', { page: 0, size: 1 }),
          getApprovalBox('cc', { page: 0, size: 1 }),
          getRecentApprovalActivities({ page: 0, size: 5 }),
        ])

        if (isActive) {
          setProfile(profileResponse)
          setPendingApprovals(pending.content)
          setRecentActivities(activities.content)
          setSummary({
            pending: pending.totalElements,
            inProgress: sent.totalElements,
            drafts: drafts.totalElements,
            cc: cc.totalElements,
          })
        }
      } catch (error) {
        if (isActive) {
          setLoadError(
            error.response?.data?.message ??
              '대시보드 정보를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) setIsLoading(false)
      }
    }

    loadDashboard()
    return () => {
      isActive = false
    }
  }, [])

  const summaryCards = [
    { label: '결재 대기', value: summary.pending, detail: '내가 처리할 문서', tone: 'violet', icon: '⌛', path: '/approvals/pending' },
    { label: '진행 중 문서', value: summary.inProgress, detail: '내가 작성한 문서', tone: 'blue', icon: '↗', path: '/approvals/sent' },
    { label: '임시저장', value: summary.drafts, detail: '작성 중인 문서', tone: 'amber', icon: '◇', path: '/approvals/drafts' },
    { label: '참조 문서', value: summary.cc, detail: '내가 참조된 문서', tone: 'green', icon: '◎', path: '/approvals/cc' },
  ]
  const dateLabel = new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date())
  const canManageUsers = ['ADMIN', 'SUPER_ADMIN'].includes(profile?.userRole)

  return (
    <div className="dashboard-page">
      <section className="welcome-panel">
        <div className="welcome-panel__glow" />
        <div className="welcome-panel__copy">
          <span className="eyebrow">{dateLabel}</span>
          <h1>안녕하세요, {profile?.userName ?? '사용자'}님.</h1>
          <p>
            {isLoading
              ? '업무 현황을 불러오는 중입니다.'
              : `현재 처리할 결재가 ${summary.pending}건 있습니다.`}
          </p>
        </div>
        <button className="primary-action" onClick={() => navigate('/approvals/new')} type="button"><span>＋</span>새 결재 작성</button>
      </section>

      {loadError && <p className="create-user-feedback">{loadError}</p>}

      <section className="summary-grid" aria-label="결재 현황 요약">
        {summaryCards.map((card) => <article className="summary-card" key={card.label}>
          <div className={`summary-card__icon summary-card__icon--${card.tone}`}>{card.icon}</div>
          <div className="summary-card__content"><span>{card.label}</span><strong>{isLoading ? '-' : card.value}</strong><small>{card.detail}</small></div>
          <button aria-label={`${card.label} 바로가기`} className="card-arrow" onClick={() => navigate(card.path)} type="button">↗</button>
        </article>)}
      </section>

      <div className="dashboard-grid">
        <section className="panel pending-panel">
          <div className="panel__header"><div><span className="section-kicker">TO DO</span><h2>결재 대기 문서</h2></div><button className="text-button" onClick={() => navigate('/approvals/pending')} type="button">전체 보기 <span>→</span></button></div>
          <div className="approval-list">
            {isLoading && <p className="compose-feedback">결재 문서를 불러오는 중입니다.</p>}
            {!isLoading && pendingApprovals.length === 0 && <p className="cc-user-empty">현재 처리할 결재 문서가 없습니다.</p>}
            {pendingApprovals.map((approval) => <button className="approval-row" key={approval.approvalId} onClick={() => navigate(`/approvals/${approval.approvalId}`, { state: { from: '/dashboard', fromLabel: '대시보드' } })} type="button">
              <span className="document-mark">결</span>
              <span className="approval-row__main"><strong>{approval.title}</strong><small>AP-{approval.approvalId} · {approval.currentStep}단계</small></span>
              <span className="approval-row__writer"><span className="mini-avatar">{approval.writerName.slice(0, 1)}</span>{approval.writerName}</span>
              <span className="approval-row__date">{formatDateTime(approval.submittedAt)}</span><span className="status-chip status-chip--pending">결재 대기</span><span className="row-chevron">›</span>
            </button>)}
          </div>
        </section>

        <aside className="panel activity-panel">
          <div className="panel__header"><div><span className="section-kicker">ACTIVITY</span><h2>최근 활동</h2></div><button aria-label="내가 작성한 문서 보기" className="more-button" onClick={() => navigate('/approvals/sent')} type="button">•••</button></div>
          {isLoading && <div className="approval-empty-state"><strong>최근 활동을 불러오는 중입니다.</strong></div>}
          {!isLoading && recentActivities.length === 0 && <div className="approval-empty-state"><strong>아직 결재 활동이 없습니다.</strong><span>문서를 상신하거나 처리하면 이곳에 표시됩니다.</span></div>}
          {!isLoading && recentActivities.length > 0 && <ol className="activity-list">
            {recentActivities.map((activity) => {
              const presentation = activityPresentation[activity.actionType] ?? {
                message: '결재 문서를 처리했습니다.',
                tone: 'pending',
              }

              return <li key={activity.approvalHistoryId}>
                <span className={`activity-dot activity-dot--${presentation.tone}`} />
                <button className="activity-link" onClick={() => navigate(`/approvals/${activity.approvalId}`, { state: { from: '/dashboard', fromLabel: '대시보드' } })} type="button">
                  <strong>{activity.actorName}님이 {presentation.message}</strong>
                  <small>{activity.title} · {formatDateTime(activity.createdAt)}</small>
                </button>
              </li>
            })}
          </ol>}
        </aside>
      </div>

      <section className="quick-actions" aria-label="빠른 작업">
        <div className="quick-actions__title"><span className="section-kicker">SHORTCUTS</span><h2>빠른 작업</h2></div>
        <button onClick={() => navigate('/approvals/new')} type="button"><span>＋</span><strong>결재 작성</strong><small>새 문서 작성하기</small></button>
        <button onClick={() => navigate('/approvals/drafts')} type="button"><span>⌁</span><strong>임시저장</strong><small>작성 중 문서 보기</small></button>
        <button onClick={() => navigate('/approvals/pending')} type="button"><span>✓</span><strong>결재 처리</strong><small>대기 문서 확인하기</small></button>
        {canManageUsers && <button onClick={() => navigate('/admin/users')} type="button"><span>♙</span><strong>사용자 관리</strong><small>조직 구성원 관리</small></button>}
      </section>
    </div>
  )
}

export default DashboardPage
