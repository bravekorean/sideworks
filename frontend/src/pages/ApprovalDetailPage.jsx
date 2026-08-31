import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import {
  approveApproval,
  cancelApproval,
  getApprovalDetail,
  rejectApproval,
} from '../api/approvalApi'
import { getMyProfile } from '../api/userApi'

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

function getDefaultBackNavigation(approval) {
  if (!approval) {
    return { path: '/dashboard', label: '대시보드' }
  }

  if (approval.approvalStatus === 'DRAFT') {
    return { path: '/approvals/drafts', label: '임시저장함' }
  }

  if (
    approval.approvalStatus === 'APPROVED' ||
    approval.approvalStatus === 'REJECTED'
  ) {
    return { path: '/approvals/processed', label: '결재 처리함' }
  }

  return { path: '/approvals/sent', label: '내가 작성한 문서' }
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 16)
}

function ApprovalDetailPage() {
  const { approvalId } = useParams()
  const location = useLocation()
  const [approval, setApproval] = useState(null)
  const [currentUserId, setCurrentUserId] = useState(null)
  const [isDetailLoading, setIsDetailLoading] = useState(true)
  const [detailLoadError, setDetailLoadError] = useState('')
  const [decisionType, setDecisionType] = useState(null)
  const [decisionComment, setDecisionComment] = useState('')
  const [isDecisionSubmitting, setIsDecisionSubmitting] = useState(false)
  const [decisionFeedback, setDecisionFeedback] = useState('')

  useEffect(() => {
    let isActive = true

    const loadApprovalDetail = async () => {
      try {
        setIsDetailLoading(true)
        setDetailLoadError('')

        const [approvalResponse, profileResponse] = await Promise.all([
          getApprovalDetail(approvalId),
          getMyProfile(),
        ])

        if (isActive) {
          setApproval(approvalResponse)
          setCurrentUserId(profileResponse.userId)
        }
      } catch (error) {
        if (isActive) {
          setDetailLoadError(
            error.response?.data?.message ??
              '결재 문서를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) {
          setIsDetailLoading(false)
        }
      }
    }

    loadApprovalDetail()

    return () => {
      isActive = false
    }
  }, [approvalId])

  const closeDecisionDialog = () => {
    if (isDecisionSubmitting) {
      return
    }

    setDecisionType(null)
    setDecisionComment('')
  }

  const handleDecisionSubmit = async (event) => {
    event.preventDefault()

    const normalizedComment = decisionComment.trim()
    if (decisionType === 'REJECTED' && !normalizedComment) {
      setDecisionFeedback('반려 사유를 입력해 주세요.')
      return
    }

    try {
      setIsDecisionSubmitting(true)
      setDecisionFeedback('')

      if (decisionType === 'APPROVED') {
        await approveApproval(approvalId, normalizedComment)
      } else if (decisionType === 'REJECTED') {
        await rejectApproval(approvalId, normalizedComment)
      } else {
        await cancelApproval(approvalId)
      }

      const refreshedApproval = await getApprovalDetail(approvalId)
      setApproval(refreshedApproval)
      setDecisionFeedback(
        decisionType === 'APPROVED'
          ? '결재 문서를 승인했습니다.'
          : decisionType === 'REJECTED'
            ? '결재 문서를 반려했습니다.'
            : '결재 문서 상신을 취소했습니다.',
      )
      setDecisionType(null)
      setDecisionComment('')
    } catch (error) {
      setDecisionFeedback(
        error.response?.data?.message ??
          '결재 문서를 처리하지 못했습니다.',
      )
    } finally {
      setIsDecisionSubmitting(false)
    }
  }

  if (isDetailLoading) {
    return (
      <div className="approval-detail-page">
        <section className="panel detail-not-found">
          <p>결재 문서를 불러오는 중입니다.</p>
        </section>
      </div>
    )
  }

  if (detailLoadError || !approval) {
    return (
      <div className="approval-detail-page">
        <section className="panel detail-not-found">
          <span>!</span>
          <h1>결재 문서를 불러올 수 없습니다.</h1>
          <p>{detailLoadError || '결재 문서 정보가 없습니다.'}</p>
          <Link to="/dashboard">대시보드로 돌아가기</Link>
        </section>
      </div>
    )
  }

  const defaultBackNavigation = getDefaultBackNavigation(approval)
  const backPath = location.state?.from ?? defaultBackNavigation.path
  const backLabel = location.state?.fromLabel ?? defaultBackNavigation.label
  const currentPendingLine = approval.approvalLines.find(
    (line) => line.approvalStatus === 'PENDING',
  )
  const canDecide =
    approval.approvalStatus === 'IN_PROGRESS' &&
    currentPendingLine?.approverId === currentUserId
  const canCancel =
    approval.approvalStatus === 'IN_PROGRESS' &&
    approval.writerId === currentUserId

  return (
    <div className="approval-detail-page">
      <div className="detail-navigation">
        <Link className="back-link" to={backPath}>
          <span aria-hidden="true">←</span>
          {backLabel}
        </Link>
        <span>AP-{approval.approvalId}</span>
      </div>

      <header className="detail-header panel">
        <div className="detail-header__main">
          <div className="detail-header__eyebrow">
            <span
              className={`document-status document-status--${approval.approvalStatus.toLowerCase()}`}
            >
              {approvalStatusLabels[approval.approvalStatus] ??
                approval.approvalStatus}
            </span>
            <span>
              {approval.approvalStatus === 'IN_PROGRESS'
                ? `현재 ${approval.currentStep}단계`
                : '처리 완료'}
            </span>
          </div>
          <h1>{approval.title}</h1>
          <div className="detail-writer">
            <span className="detail-avatar">
              {approval.writerName.slice(0, 1)}
            </span>
            <div>
              <strong>{approval.writerName}</strong>
              <small>
                작성자 · {formatDateTime(approval.submittedAt)} 상신
              </small>
            </div>
          </div>
        </div>

        {(canDecide || canCancel) && (
          <div className="detail-actions">
            {canCancel && (
              <button
                className="decision-button decision-button--cancel"
                onClick={() => {
                  setDecisionFeedback('')
                  setDecisionType('CANCELED')
                }}
                type="button"
              >
                상신 취소
              </button>
            )}
            {canDecide && (
              <>
            <button
              className="decision-button decision-button--reject"
              onClick={() => {
                setDecisionFeedback('')
                setDecisionType('REJECTED')
              }}
              type="button"
            >
              반려
            </button>
            <button
              className="decision-button decision-button--approve"
              onClick={() => {
                setDecisionFeedback('')
                setDecisionType('APPROVED')
              }}
              type="button"
            >
              승인
            </button>
              </>
            )}
          </div>
        )}
      </header>

      {decisionFeedback && !decisionType && (
        <div aria-live="polite" className="detail-feedback">
          {decisionFeedback}
        </div>
      )}

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
            {approval.histories.length > 0 ? (
              <ol className="detail-history-list">
                {approval.histories.map((history) => (
                  <li key={history.approvalHistoryId}>
                    <span
                      className={`history-marker history-marker--${history.actionType.toLowerCase()}`}
                    />
                    <div className="history-content">
                      <div>
                        <strong>{history.actorName}</strong>
                        <span>
                          {actionTypeLabels[history.actionType] ??
                            history.actionType}
                        </span>
                      </div>
                      <time>{formatDateTime(history.createdAt)}</time>
                      {history.comment && <p>{history.comment}</p>}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="cc-user-empty">저장된 처리 이력이 없습니다.</p>
            )}
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
            {approval.approvalLines.length > 0 ? (
              <ol className="approval-line-list">
                {approval.approvalLines.map((line) => (
                  <li
                    className={`approval-line-item approval-line-item--${line.approvalStatus.toLowerCase()}`}
                    key={line.approvalLineId}
                  >
                    <div className="approval-step">{line.approvalStep}</div>
                    <div className="approval-line-user">
                      <strong>{line.approverName}</strong>
                      <span>
                        {lineStatusLabels[line.approvalStatus] ??
                          line.approvalStatus}
                      </span>
                      {line.processedAt && (
                        <time>{formatDateTime(line.processedAt)}</time>
                      )}
                      {line.approvalComment && (
                        <p>{line.approvalComment}</p>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="cc-user-empty">지정된 결재자가 없습니다.</p>
            )}
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
              {decisionType === 'CANCELED'
                ? '결재 문서 상신을 취소할까요?'
                : `문서를 ${decisionType === 'APPROVED' ? '승인' : '반려'}할까요?`}
            </h2>
            <p>
              {decisionType === 'CANCELED'
                ? '취소한 문서는 더 이상 결재를 진행할 수 없습니다.'
                : '처리 결과는 즉시 결재선과 처리 이력에 반영됩니다.'}
            </p>
            <form onSubmit={handleDecisionSubmit}>
              {decisionType !== 'CANCELED' && (
                <>
              <label htmlFor="decision-comment">
                {decisionType === 'APPROVED' ? '의견' : '반려 사유'}
              </label>
              <textarea
                disabled={isDecisionSubmitting}
                id="decision-comment"
                onChange={(event) =>
                  setDecisionComment(event.target.value)
                }
                placeholder={
                  decisionType === 'APPROVED'
                    ? '승인 의견을 입력하세요. (선택)'
                    : '반려 사유를 입력하세요.'
                }
                required={decisionType === 'REJECTED'}
                rows="4"
                value={decisionComment}
              />
                </>
              )}
              {decisionFeedback && (
                <p aria-live="polite" className="compose-feedback">
                  {decisionFeedback}
                </p>
              )}
              <div className="decision-dialog__actions">
                <button
                  disabled={isDecisionSubmitting}
                  onClick={closeDecisionDialog}
                  type="button"
                >
                  취소
                </button>
                <button
                  className={
                    decisionType === 'APPROVED'
                      ? 'dialog-confirm--approve'
                      : 'dialog-confirm--reject'
                  }
                  disabled={isDecisionSubmitting}
                  type="submit"
                >
                  {isDecisionSubmitting
                    ? '처리 중...'
                    : decisionType === 'APPROVED'
                      ? '승인하기'
                      : decisionType === 'REJECTED'
                        ? '반려하기'
                        : '상신 취소'}
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
