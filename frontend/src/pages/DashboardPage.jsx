const summaryCards = [
  { label: '결재 대기', value: '7', detail: '오늘 마감 2건', tone: 'violet', icon: '⌛' },
  { label: '진행 중 문서', value: '12', detail: '이번 주 +4건', tone: 'blue', icon: '↗' },
  { label: '임시저장', value: '3', detail: '7일 이상 1건', tone: 'amber', icon: '◇' },
  { label: '참조 문서', value: '9', detail: '새 문서 2건', tone: 'green', icon: '◎' },
]

const pendingApprovals = [
  { id: 'AP-2026-071', title: '개발팀 업무용 노트북 구매 요청', writer: '박준호', type: '비품 구매', date: '오늘 09:42', status: '결재 대기' },
  { id: 'AP-2026-068', title: '2026년 하반기 교육비 지원 신청', writer: '이서윤', type: '교육 신청', date: '어제 16:18', status: '결재 대기' },
  { id: 'AP-2026-063', title: '7월 프로젝트 외근 교통비 정산', writer: '최민석', type: '비용 정산', date: '7월 20일', status: '결재 대기' },
  { id: 'AP-2026-059', title: '신규 개발 서버 계정 발급 요청', writer: '정하늘', type: '계정 신청', date: '7월 19일', status: '결재 대기' },
]

const activities = [
  { title: '노트북 구매 요청이 승인되었습니다.', meta: '김관리 · 12분 전', tone: 'approved' },
  { title: '새 결재 문서가 도착했습니다.', meta: '박준호 · 1시간 전', tone: 'pending' },
  { title: '교육비 신청이 반려되었습니다.', meta: '이서윤 · 어제 17:32', tone: 'rejected' },
  { title: '프로젝트 외근비가 상신되었습니다.', meta: '최민석 · 어제 15:08', tone: 'submitted' },
]

function DashboardPage() {
  return (
    <div className="dashboard-page">
      <section className="welcome-panel">
        <div className="welcome-panel__glow" />
        <div className="welcome-panel__copy">
          <span className="eyebrow">WEDNESDAY · JULY 22</span>
          <h1>좋은 오후예요, 김관리님.</h1>
          <p>처리할 결재가 7건 있습니다. 중요한 문서부터 확인해 보세요.</p>
        </div>
        <button className="primary-action" type="button">
          <span>＋</span>
          새 결재 작성
        </button>
      </section>

      <section className="summary-grid" aria-label="결재 현황 요약">
        {summaryCards.map((card) => (
          <article className="summary-card" key={card.label}>
            <div className={`summary-card__icon summary-card__icon--${card.tone}`}>{card.icon}</div>
            <div className="summary-card__content">
              <span>{card.label}</span>
              <strong>{card.value}</strong>
              <small>{card.detail}</small>
            </div>
            <button aria-label={`${card.label} 바로가기`} className="card-arrow" type="button">↗</button>
          </article>
        ))}
      </section>

      <div className="dashboard-grid">
        <section className="panel pending-panel">
          <div className="panel__header">
            <div>
              <span className="section-kicker">TO DO</span>
              <h2>결재 대기 문서</h2>
            </div>
            <button className="text-button" type="button">전체 보기 <span>→</span></button>
          </div>

          <div className="approval-list">
            {pendingApprovals.map((approval) => (
              <button className="approval-row" key={approval.id} type="button">
                <span className="document-mark">{approval.type.slice(0, 1)}</span>
                <span className="approval-row__main">
                  <strong>{approval.title}</strong>
                  <small>{approval.id} · {approval.type}</small>
                </span>
                <span className="approval-row__writer">
                  <span className="mini-avatar">{approval.writer.slice(0, 1)}</span>
                  {approval.writer}
                </span>
                <span className="approval-row__date">{approval.date}</span>
                <span className="status-chip status-chip--pending">{approval.status}</span>
                <span className="row-chevron">›</span>
              </button>
            ))}
          </div>
        </section>

        <aside className="panel activity-panel">
          <div className="panel__header">
            <div>
              <span className="section-kicker">ACTIVITY</span>
              <h2>최근 활동</h2>
            </div>
            <button aria-label="최근 활동 옵션" className="more-button" type="button">•••</button>
          </div>

          <ol className="activity-list">
            {activities.map((activity) => (
              <li key={`${activity.title}-${activity.meta}`}>
                <span className={`activity-dot activity-dot--${activity.tone}`} />
                <div>
                  <strong>{activity.title}</strong>
                  <small>{activity.meta}</small>
                </div>
              </li>
            ))}
          </ol>
        </aside>
      </div>

      <section className="quick-actions" aria-label="빠른 작업">
        <div className="quick-actions__title">
          <span className="section-kicker">SHORTCUTS</span>
          <h2>빠른 작업</h2>
        </div>
        <button type="button"><span>＋</span><strong>결재 작성</strong><small>새 문서 작성하기</small></button>
        <button type="button"><span>⌁</span><strong>임시저장</strong><small>작성 중 문서 보기</small></button>
        <button type="button"><span>✓</span><strong>결재 처리</strong><small>대기 문서 확인하기</small></button>
        <button type="button"><span>♙</span><strong>사용자 관리</strong><small>조직 구성원 관리</small></button>
      </section>
    </div>
  )
}

export default DashboardPage
