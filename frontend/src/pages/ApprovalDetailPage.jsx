import { useState } from 'react'
import { Link, useParams } from 'react-router'

const approvalStatusLabels = {
  DRAFT: '임시저장',
  IN_PROGRESS: '결재 진행',
  APPROVED: '승인 완료',
  REJECTED: '반려',
  CANCELED: '상신 취소',
}

const lineStatusLabels = {
  WAITING: '결재 예정',
  PENDING: '결재 대기',
  APPROVED: '승인',
  REJECTED: '반려',
}

const actionTypeLabels = {
  SUBMITTED: '상신',
  APPROVED: '승인',
  REJECTED: '반려',
  CANCELED: '상신 취소',
}

const mockApprovalDetails = {
  71: {
    approvalId: 71,
    writerId: 4,
    writerName: '박준호',
    title: '개발팀 업무용 노트북 구매 요청',
    content:
      '신규 입사자와 개발 환경 개선을 위해 업무용 노트북 3대 구매를 요청합니다.\n\n현재 사용 중인 장비는 빌드 및 로컬 테스트 과정에서 성능 저하가 반복되고 있습니다. 개발 생산성과 장애 대응 속도를 고려하여 표준 사양 장비로 교체하고자 합니다.',
    approvalStatus: 'IN_PROGRESS',
    currentStep: 1,
    createdAt: '2026-07-28T09:20:00',
    updatedAt: '2026-07-28T09:42:00',
    submittedAt: '2026-07-28T09:42:00',
    completedAt: null,
    approvalLines: [
      {
        approvalLineId: 101,
        approverId: 1,
        approverName: '김관리',
        approvalStep: 1,
        approvalStatus: 'PENDING',
        approvalComment: null,
        processedAt: null,
      },
      {
        approvalLineId: 102,
        approverId: 2,
        approverName: '이총괄',
        approvalStep: 2,
        approvalStatus: 'WAITING',
        approvalComment: null,
        processedAt: null,
      },
    ],
    ccUsers: [
      { userId: 7, userName: '정하늘' },
      { userId: 9, userName: '오세진' },
    ],
    histories: [
      {
        approvalHistoryId: 201,
        actorId: 4,
        actorName: '박준호',
        actionStep: 1,
        actionType: 'SUBMITTED',
        comment: '장비 견적서를 확인해 주세요.',
        createdAt: '2026-07-28T09:42:00',
      },
    ],
  },
  68: {
    approvalId: 68,
    writerId: 5,
    writerName: '이서윤',
    title: '2026년 하반기 교육비 지원 신청',
    content:
      '하반기 백엔드 아키텍처 교육 과정 수강을 위한 교육비 지원을 신청합니다.\n\n교육을 통해 대용량 트래픽 처리와 데이터베이스 성능 개선 역량을 강화하고 팀 내 기술 공유 세션을 진행할 예정입니다.',
    approvalStatus: 'IN_PROGRESS',
    currentStep: 2,
    createdAt: '2026-07-27T15:50:00',
    updatedAt: '2026-07-28T08:30:00',
    submittedAt: '2026-07-27T16:18:00',
    completedAt: null,
    approvalLines: [
      {
        approvalLineId: 103,
        approverId: 3,
        approverName: '최팀장',
        approvalStep: 1,
        approvalStatus: 'APPROVED',
        approvalComment: '업무 연관성이 높아 승인합니다.',
        processedAt: '2026-07-28T08:30:00',
      },
      {
        approvalLineId: 104,
        approverId: 1,
        approverName: '김관리',
        approvalStep: 2,
        approvalStatus: 'PENDING',
        approvalComment: null,
        processedAt: null,
      },
      {
        approvalLineId: 105,
        approverId: 2,
        approverName: '이총괄',
        approvalStep: 3,
        approvalStatus: 'WAITING',
        approvalComment: null,
        processedAt: null,
      },
    ],
    ccUsers: [{ userId: 8, userName: '한유진' }],
    histories: [
      {
        approvalHistoryId: 202,
        actorId: 5,
        actorName: '이서윤',
        actionStep: 1,
        actionType: 'SUBMITTED',
        comment: null,
        createdAt: '2026-07-27T16:18:00',
      },
      {
        approvalHistoryId: 203,
        actorId: 3,
        actorName: '최팀장',
        actionStep: 1,
        actionType: 'APPROVED',
        comment: '업무 연관성이 높아 승인합니다.',
        createdAt: '2026-07-28T08:30:00',
      },
    ],
  },
  63: {
    approvalId: 63,
    writerId: 6,
    writerName: '최민석',
    title: '7월 프로젝트 외근 교통비 정산',
    content:
      '7월 고객사 방문에 사용한 교통비 정산을 요청합니다.\n\n방문 목적은 배포 전 최종 사용자 검수와 운영 담당자 교육이었습니다.',
    approvalStatus: 'IN_PROGRESS',
    currentStep: 1,
    createdAt: '2026-07-26T10:40:00',
    updatedAt: '2026-07-26T11:05:00',
    submittedAt: '2026-07-26T11:05:00',
    completedAt: null,
    approvalLines: [
      {
        approvalLineId: 106,
        approverId: 1,
        approverName: '김관리',
        approvalStep: 1,
        approvalStatus: 'PENDING',
        approvalComment: null,
        processedAt: null,
      },
    ],
    ccUsers: [],
    histories: [
      {
        approvalHistoryId: 204,
        actorId: 6,
        actorName: '최민석',
        actionStep: 1,
        actionType: 'SUBMITTED',
        comment: null,
        createdAt: '2026-07-26T11:05:00',
      },
    ],
  },
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 16)
}

function ApprovalDetailPage() {
  const { approvalId } = useParams()
  const [decisionType, setDecisionType] = useState(null)
  const [decisionComment, setDecisionComment] = useState('')
  const approval = mockApprovalDetails[approvalId]

  if (!approval) {
    return (
      <div className="approval-detail-page">
        <section className="panel detail-not-found">
          <span>404</span>
          <h1>결재 문서를 찾을 수 없습니다.</h1>
          <p>삭제되었거나 조회 권한이 없는 문서일 수 있습니다.</p>
          <Link to="/approvals/pending">결재 대기함으로 돌아가기</Link>
        </section>
      </div>
    )
  }

  const closeDecisionDialog = () => {
    setDecisionType(null)
    setDecisionComment('')
  }

  const handleDecisionSubmit = (event) => {
    event.preventDefault()
    closeDecisionDialog()
  }

  return (
    <div className="approval-detail-page">
      <div className="detail-navigation">
        <Link className="back-link" to="/approvals/pending">
          <span aria-hidden="true">←</span>
          결재 대기함
        </Link>
        <span>AP-{approval.approvalId}</span>
      </div>

      <header className="detail-header panel">
        <div className="detail-header__main">
          <div className="detail-header__eyebrow">
            <span
              className={`document-status document-status--${approval.approvalStatus.toLowerCase()}`}
            >
              {approvalStatusLabels[approval.approvalStatus]}
            </span>
            <span>현재 {approval.currentStep}단계</span>
          </div>
          <h1>{approval.title}</h1>
          <div className="detail-writer">
            <span className="detail-avatar">
              {approval.writerName.slice(0, 1)}
            </span>
            <div>
              <strong>{approval.writerName}</strong>
              <small>작성자 · {formatDateTime(approval.submittedAt)} 상신</small>
            </div>
          </div>
        </div>

        <div className="detail-actions">
          <button
            className="decision-button decision-button--reject"
            onClick={() => setDecisionType('REJECTED')}
            type="button"
          >
            반려
          </button>
          <button
            className="decision-button decision-button--approve"
            onClick={() => setDecisionType('APPROVED')}
            type="button"
          >
            승인
          </button>
        </div>
      </header>

      <div className="approval-detail-grid">
        <div className="approval-detail-main">
          <section className="panel document-content-panel">
            <div className="detail-section-header">
              <div>
                <span className="section-kicker">DOCUMENT</span>
                <h2>문서 내용</h2>
              </div>
              <span>최종 수정 {formatDateTime(approval.updatedAt)}</span>
            </div>
            <div className="document-content">
              {approval.content.split('\n').map((paragraph, index) =>
                paragraph ? (
                  <p key={`${paragraph}-${index}`}>{paragraph}</p>
                ) : (
                  <br key={`line-break-${index}`} />
                ),
              )}
            </div>
          </section>

          <section className="panel detail-history-panel">
            <div className="detail-section-header">
              <div>
                <span className="section-kicker">HISTORY</span>
                <h2>처리 이력</h2>
              </div>
            </div>
            <ol className="detail-history-list">
              {approval.histories.map((history) => (
                <li key={history.approvalHistoryId}>
                  <span
                    className={`history-marker history-marker--${history.actionType.toLowerCase()}`}
                  />
                  <div className="history-content">
                    <div>
                      <strong>{history.actorName}</strong>
                      <span>{actionTypeLabels[history.actionType]}</span>
                    </div>
                    <time>{formatDateTime(history.createdAt)}</time>
                    {history.comment && <p>{history.comment}</p>}
                  </div>
                </li>
              ))}
            </ol>
          </section>
        </div>

        <aside className="approval-detail-side">
          <section className="panel approval-line-panel">
            <div className="detail-section-header">
              <div>
                <span className="section-kicker">APPROVAL LINE</span>
                <h2>결재선</h2>
              </div>
              <span>{approval.approvalLines.length}명</span>
            </div>
            <ol className="approval-line-list">
              {approval.approvalLines.map((line) => (
                <li
                  className={`approval-line-item approval-line-item--${line.approvalStatus.toLowerCase()}`}
                  key={line.approvalLineId}
                >
                  <div className="approval-step">{line.approvalStep}</div>
                  <div className="approval-line-user">
                    <strong>{line.approverName}</strong>
                    <span>{lineStatusLabels[line.approvalStatus]}</span>
                    {line.processedAt && (
                      <time>{formatDateTime(line.processedAt)}</time>
                    )}
                    {line.approvalComment && <p>{line.approvalComment}</p>}
                  </div>
                </li>
              ))}
            </ol>
          </section>

          <section className="panel cc-user-panel">
            <div className="detail-section-header">
              <div>
                <span className="section-kicker">CC</span>
                <h2>참조자</h2>
              </div>
            </div>
            {approval.ccUsers.length > 0 ? (
              <div className="cc-user-list">
                {approval.ccUsers.map((user) => (
                  <span key={user.userId}>
                    <span>{user.userName.slice(0, 1)}</span>
                    {user.userName}
                  </span>
                ))}
              </div>
            ) : (
              <p className="cc-user-empty">지정된 참조자가 없습니다.</p>
            )}
          </section>

          <section className="panel document-meta-panel">
            <div className="detail-section-header">
              <div>
                <span className="section-kicker">INFORMATION</span>
                <h2>문서 정보</h2>
              </div>
            </div>
            <dl>
              <div>
                <dt>문서 ID</dt>
                <dd>{approval.approvalId}</dd>
              </div>
              <div>
                <dt>작성일</dt>
                <dd>{formatDateTime(approval.createdAt)}</dd>
              </div>
              <div>
                <dt>상신일</dt>
                <dd>{formatDateTime(approval.submittedAt)}</dd>
              </div>
              <div>
                <dt>완료일</dt>
                <dd>{formatDateTime(approval.completedAt)}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>

      {decisionType && (
        <div className="decision-dialog-backdrop">
          <section
            aria-labelledby="decision-dialog-title"
            aria-modal="true"
            className="decision-dialog"
            role="dialog"
          >
            <div className="decision-dialog__icon">
              {decisionType === 'APPROVED' ? '✓' : '!'}
            </div>
            <h2 id="decision-dialog-title">
              문서를 {decisionType === 'APPROVED' ? '승인' : '반려'}할까요?
            </h2>
            <p>
              현재 화면은 목 데이터로 동작하며, 실제 처리는 API 연결 후
              반영됩니다.
            </p>
            <form onSubmit={handleDecisionSubmit}>
              <label htmlFor="decision-comment">
                {decisionType === 'APPROVED' ? '의견' : '반려 사유'}
              </label>
              <textarea
                id="decision-comment"
                onChange={(event) => setDecisionComment(event.target.value)}
                placeholder={
                  decisionType === 'APPROVED'
                    ? '승인 의견을 입력하세요. (선택)'
                    : '반려 사유를 입력하세요.'
                }
                required={decisionType === 'REJECTED'}
                rows="4"
                value={decisionComment}
              />
              <div className="decision-dialog__actions">
                <button onClick={closeDecisionDialog} type="button">
                  취소
                </button>
                <button
                  className={
                    decisionType === 'APPROVED'
                      ? 'dialog-confirm--approve'
                      : 'dialog-confirm--reject'
                  }
                  type="submit"
                >
                  {decisionType === 'APPROVED' ? '승인하기' : '반려하기'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

export default ApprovalDetailPage
