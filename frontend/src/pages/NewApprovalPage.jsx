import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  createDraft,
  deleteDraft,
  getApprovalDetail,
  submitApproval,
  updateDraft,
} from '../api/approvalApi'
import { getDirectory } from '../api/userApi'

const documentTypes = [
  '품의서',
  '비품 구매',
  '비용 정산',
  '교육 신청',
  '근무 신청',
]

function NewApprovalPage() {
  const { approvalId } = useParams()
  const navigate = useNavigate()
  const isEditing = Boolean(approvalId)

  // 결재 문서 입력 상태
  const [documentType, setDocumentType] = useState(documentTypes[0])
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [approverIds, setApproverIds] = useState([])
  const [ccUserIds, setCcUserIds] = useState([])

  // 임시저장 문서 조회 상태
  const [isDraftLoading, setIsDraftLoading] = useState(isEditing)
  const [draftLoadError, setDraftLoadError] = useState('')

  // 조직 구성원 조회 상태
  const [directoryUsers, setDirectoryUsers] = useState([])
  const [isDirectoryLoading, setIsDirectoryLoading] = useState(true)
  const [directoryLoadError, setDirectoryLoadError] = useState('')

  // 저장 및 상신 결과 상태
  const [feedback, setFeedback] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  // 수정 화면에서 임시저장 문서 조회
  useEffect(() => {
    if (!isEditing) {
      return undefined
    }

    let isActive = true

    const loadDraft = async () => {
      try {
        setIsDraftLoading(true)
        setDraftLoadError('')

        const approval = await getApprovalDetail(approvalId)

        if (!isActive) {
          return
        }

        if (approval.approvalStatus !== 'DRAFT') {
          setDraftLoadError('이미 상신되었거나 수정할 수 없는 문서입니다.')
          return
        }

        setTitle(approval.title)
        setContent(approval.content)
      } catch (error) {
        if (isActive) {
          setDraftLoadError(
            error.response?.data?.message ??
              '임시저장 문서를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) {
          setIsDraftLoading(false)
        }
      }
    }

    loadDraft()

    return () => {
      isActive = false
    }
  }, [approvalId, isEditing])

  // 결재자·참조자 선택을 위한 조직 구성원 조회
  useEffect(() => {
    let isActive = true

    const loadDirectoryUsers = async () => {
      try {
        setIsDirectoryLoading(true)
        setDirectoryLoadError('')

        const pageResponse = await getDirectory(0, 100)

        if (isActive) {
          setDirectoryUsers(pageResponse.content)
        }
      } catch (error) {
        if (isActive) {
          setDirectoryLoadError(
            error.response?.data?.message ??
              '조직 구성원을 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) {
          setIsDirectoryLoading(false)
        }
      }
    }

    loadDirectoryUsers()

    return () => {
      isActive = false
    }
  }, [])

  const availableApprovers = directoryUsers.filter(
    (user) =>
      user.userRole === 'ADMIN' || user.userRole === 'SUPER_ADMIN',
  )
  const availableCcUsers = directoryUsers

  const toggleApprover = (userId) => {
    if (ccUserIds.includes(userId)) {
      setFeedback(
        '이미 참조자로 선택된 사용자입니다. 참조자 선택을 먼저 해제해 주세요.',
      )
      return
    }

    setApproverIds((currentIds) =>
      currentIds.includes(userId)
        ? currentIds.filter((id) => id !== userId)
        : [...currentIds, userId],
    )
    setFeedback('')
  }

  const toggleCcUser = (userId) => {
    if (approverIds.includes(userId)) {
      setFeedback(
        '이미 결재자로 선택된 사용자입니다. 결재자 선택을 먼저 해제해 주세요.',
      )
      return
    }

    setCcUserIds((currentIds) =>
      currentIds.includes(userId)
        ? currentIds.filter((id) => id !== userId)
        : [...currentIds, userId],
    )

    setFeedback('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (isSaving || isSubmitting) {
      return
    }

    if (!title.trim() || !content.trim() || approverIds.length === 0) {
      setFeedback('제목, 내용, 결재자를 모두 입력해 주세요.')
      return
    }

    let targetApprovalId = approvalId

    try {
      setIsSubmitting(true)
      setFeedback('결재 문서를 상신하는 중입니다.')

      if (isEditing) {
        await updateDraft(
          targetApprovalId,
          title.trim(),
          content.trim(),
        )
      } else {
        targetApprovalId = await createDraft(
          title.trim(),
          content.trim(),
        )

        // 상신에 실패해도 생성된 임시저장을 다시 수정할 수 있게 URL을 보존한다.
        navigate(`/approvals/${targetApprovalId}/edit`, {
          replace: true,
        })
      }

      await submitApproval(targetApprovalId, approverIds, ccUserIds)

      navigate(`/approvals/${targetApprovalId}`, {
        replace: true,
      })
    } catch (error) {
      setFeedback(
        error.response?.data?.message ??
          '결재 문서를 상신하지 못했습니다.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleSaveDraft = async () => {
    if (!title.trim() || !content.trim()) {
      setFeedback('제목과 내용을 입력해 주세요.')
      return
    }

    try {
      setIsSaving(true)
      setFeedback('임시저장 중입니다.')

      if (isEditing) {
        await updateDraft(approvalId, title.trim(), content.trim())
        setFeedback('임시저장 문서를 수정했습니다.')
        return
      }

      const createdApprovalId = await createDraft(
        title.trim(),
        content.trim(),
      )

      setFeedback('임시저장되었습니다.')
      navigate(`/approvals/${createdApprovalId}/edit`, {
        replace: true,
      })
    } catch (error) {
      setFeedback(
        error.response?.data?.message ??
          '임시저장 중 오류가 발생했습니다.',
      )
    } finally {
      setIsSaving(false)
    }
  }

  const handleDeleteDraft = async () => {
    const shouldDelete = window.confirm(
      '이 임시저장 문서를 삭제할까요? 삭제 후에는 복구할 수 없습니다.',
    )

    if (!shouldDelete) {
      return
    }

    try {
      setIsDeleting(true)
      setFeedback('임시저장 문서를 삭제하는 중입니다.')
      await deleteDraft(approvalId)
      navigate('/approvals/drafts', { replace: true })
    } catch (error) {
      setFeedback(
        error.response?.data?.message ??
          '임시저장 문서를 삭제하지 못했습니다.',
      )
    } finally {
      setIsDeleting(false)
    }
  }

  if (isDraftLoading) {
    return (
      <div className="approval-detail-page">
        <section className="panel detail-not-found">
          <p>임시저장 문서를 불러오는 중입니다.</p>
        </section>
      </div>
    )
  }

  if (draftLoadError) {
    return (
      <div className="approval-detail-page">
        <section className="panel detail-not-found">
          <span>!</span>
          <h1>문서를 불러올 수 없습니다.</h1>
          <p>{draftLoadError}</p>
          <Link to="/approvals/drafts">임시저장함으로 돌아가기</Link>
        </section>
      </div>
    )
  }

  return (
    <div className="approval-compose-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">
            {isEditing ? 'EDIT DRAFT' : 'NEW APPROVAL'}
          </span>
          <h1>{isEditing ? '임시저장 문서 수정' : '새 결재 작성'}</h1>
          <p>
            {isEditing
              ? '저장된 내용을 수정하고 결재선을 확인한 뒤 상신합니다.'
              : '결재 내용과 결재선을 지정하여 새로운 문서를 상신합니다.'}
          </p>
        </div>
        <span className="compose-draft-state">
          {isEditing ? `DRAFT-${approvalId}` : '작성 중'}
        </span>
      </header>

      <form className="compose-layout" onSubmit={handleSubmit}>
        <div className="compose-main">
          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">01</span>
              <div>
                <h2>문서 정보</h2>
                <p>문서 종류와 제목을 입력해 주세요.</p>
              </div>
            </div>

            <div className="compose-form-grid">
              <label className="form-field">
                <span>문서 종류</span>
                <select
                  onChange={(event) => setDocumentType(event.target.value)}
                  value={documentType}
                >
                  {documentTypes.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-field form-field--wide">
                <span>제목</span>
                <input
                  maxLength={200}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="결재 문서 제목을 입력하세요."
                  value={title}
                />
                <small>{title.length} / 200</small>
              </label>
            </div>
          </section>

          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">02</span>
              <div>
                <h2>결재 내용</h2>
                <p>검토자가 이해할 수 있도록 요청 배경과 내용을 작성해 주세요.</p>
              </div>
            </div>

            <label className="form-field">
              <span className="sr-only">결재 내용</span>
              <textarea
                onChange={(event) => setContent(event.target.value)}
                placeholder="결재 내용을 입력하세요."
                value={content}
              />
              <small>{content.length.toLocaleString()}자</small>
            </label>
          </section>
        </div>

        <aside className="compose-side">
          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">03</span>
              <div>
                <h2>결재선</h2>
                <p>승인 순서대로 결재자를 선택합니다.</p>
              </div>
            </div>

            {isDirectoryLoading && (
              <p className="compose-feedback">
                결재자 후보를 불러오는 중입니다.
              </p>
            )}
            {directoryLoadError && (
              <p className="compose-feedback">{directoryLoadError}</p>
            )}
            {!isDirectoryLoading &&
              !directoryLoadError &&
              availableApprovers.length === 0 && (
                <p className="compose-feedback">
                  선택할 수 있는 결재자가 없습니다.
                </p>
              )}

            <div className="selectable-user-list">
              {availableApprovers.map((approver) => {
                const selected = approverIds.includes(approver.userId)
                const approvalStep = approverIds.indexOf(approver.userId) + 1

                return (
                  <label
                    className={`selectable-user ${selected ? 'selectable-user--selected' : ''}`}
                    key={approver.userId}
                  >
                    <input
                      checked={selected}
                      onChange={() => toggleApprover(approver.userId)}
                      type="checkbox"
                    />
                    <span className="selectable-user__avatar">
                      {selected
                        ? approvalStep
                        : approver.userName.slice(0, 1)}
                    </span>
                    <span className="selectable-user__copy">
                      <strong>{approver.userName}</strong>
                      <small>
                        {approver.departmentName ?? '부서 미배정'} ·{' '}
                        {approver.positionName ?? '직급 미배정'}
                      </small>
                    </span>
                  </label>
                )
              })}
            </div>
          </section>

          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">04</span>
              <div>
                <h2>참조자</h2>
                <p>문서를 함께 확인할 구성원을 선택합니다.</p>
              </div>
            </div>

            <div className="cc-chip-list">
              {availableCcUsers.map((user) => (
                <label
                  className={`cc-select-chip ${ccUserIds.includes(user.userId) ? 'cc-select-chip--selected' : ''}`}
                  key={user.userId}
                >
                  <input
                    checked={ccUserIds.includes(user.userId)}
                    onChange={() => toggleCcUser(user.userId)}
                    type="checkbox"
                  />
                  <span>{user.userName}</span>
                  <small>{user.departmentName ?? '부서 미배정'}</small>
                </label>
              ))}
            </div>
          </section>
        </aside>

        <footer className="compose-actions">
          <div aria-live="polite" className="compose-feedback">
            {feedback}
          </div>
          {isEditing && (
            <>
              <Link className="compose-back-link" to="/approvals/drafts">
                목록으로
              </Link>
              <button
                className="compose-button compose-button--danger"
                disabled={isSaving || isSubmitting || isDeleting}
                onClick={handleDeleteDraft}
                type="button"
              >
                {isDeleting ? '삭제 중...' : '임시저장 삭제'}
              </button>
            </>
          )}
          <button
            className="compose-button compose-button--secondary"
            disabled={isSaving || isSubmitting || isDeleting}
            onClick={handleSaveDraft}
            type="button"
          >
            {isSaving ? '저장 중...' : '임시저장'}
          </button>
          <button
            className="compose-button compose-button--primary"
            disabled={isSaving || isSubmitting || isDeleting}
            type="submit"
          >
            {isSubmitting ? '상신 중...' : '결재 상신'}
          </button>
        </footer>
      </form>
    </div>
  )
}

export default NewApprovalPage
