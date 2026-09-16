import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  createDraft,
  deleteApprovalAttachment,
  deleteDraft,
  getApprovalDetail,
  submitApproval,
  uploadApprovalAttachments,
  updateDraft,
} from '../api/approvalApi'
import { getDirectory } from '../api/userApi'
import { getCorrectionApprovers, getCorrectionRecord, submitCorrectionDocument } from '../api/attendanceCorrectionApi'

const documentTypes = [
  '품의서',
  '비품 구매',
  '비용 정산',
  '교육 신청',
  '근무 신청',
  '근태 정정',
]

function NewApprovalPage() {
  const { approvalId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const isEditing = Boolean(approvalId)

  // 결재 문서 입력 상태
  const [documentType, setDocumentType] = useState(!approvalId && searchParams.get('type') === 'attendance-correction' ? '근태 정정' : documentTypes[0])
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [approverIds, setApproverIds] = useState([])
  const [ccUserIds, setCcUserIds] = useState([])
  const [attachments, setAttachments] = useState([])
  const [pendingFiles, setPendingFiles] = useState([])
  const isCorrection = documentType === '근태 정정'
  const [correctionDate, setCorrectionDate] = useState(searchParams.get('date') || '')
  const [correctionRecord, setCorrectionRecord] = useState(null)
  const [correctionCheckIn, setCorrectionCheckIn] = useState('')
  const [correctionCheckOut, setCorrectionCheckOut] = useState('')
  const [correctionReason, setCorrectionReason] = useState('')
  const [correctionApprovers, setCorrectionApprovers] = useState([])
  const [correctionLoadError, setCorrectionLoadError] = useState('')
  const [correctionApproversError, setCorrectionApproversError] = useState('')
  const [correctionReload, setCorrectionReload] = useState(0)

  useEffect(() => {
    if (!isCorrection) return undefined
    let active = true
    getCorrectionApprovers().then((choices) => {
      if (active) { setCorrectionApprovers(choices); setCorrectionApproversError('') }
    }).catch((error) => {
      if (active) setCorrectionApproversError(error.response?.data?.message ?? '정정 결재자 목록을 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [isCorrection])

  useEffect(() => {
    if (!isCorrection || !correctionDate) return undefined
    let active = true
    getCorrectionRecord({ date: correctionDate }).then((record) => {
      if (!active) return
      setCorrectionRecord({ ...record, date: correctionDate })
      setCorrectionCheckIn(record.checkInAt?.slice(0, 19) || `${correctionDate}T09:00:00`)
      setCorrectionCheckOut(record.checkOutAt?.slice(0, 19) || '')
      setCorrectionLoadError('')
    }).catch((error) => {
      if (active) setCorrectionLoadError(error.response?.data?.message ?? '기존 근태를 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [isCorrection, correctionDate, correctionReload])

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
        setAttachments(approval.attachments ?? [])
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

  const availableApprovers = isCorrection ? correctionApprovers : directoryUsers.filter(
    (user) =>
      user.teamLeader ||
      ['HR_MANAGER', 'SUPER_ADMIN'].includes(user.userRole),
  )
  const availableCcUsers = directoryUsers
  const attachmentSize = attachments.reduce((sum, file) => sum + file.fileSize, 0)
    + pendingFiles.reduce((sum, file) => sum + file.size, 0)

  const handleFileSelection = (event) => {
    const selected = [...event.target.files]
    const nextCount = attachments.length + pendingFiles.length + selected.length
    const nextSize = attachmentSize + selected.reduce((sum, file) => sum + file.size, 0)
    if (selected.some((file) => file.size > 20 * 1024 * 1024)) {
      setFeedback('파일 하나의 크기는 20MB를 넘을 수 없습니다.')
    } else if (nextCount > 5 || nextSize > 100 * 1024 * 1024) {
      setFeedback('첨부파일은 최대 5개, 총 100MB까지 등록할 수 있습니다.')
    } else {
      setPendingFiles((current) => [...current, ...selected])
      setFeedback('')
    }
    event.target.value = ''
  }

  const uploadPendingFiles = async (targetApprovalId) => {
    if (pendingFiles.length === 0) return
    const uploaded = await uploadApprovalAttachments(targetApprovalId, pendingFiles)
    setAttachments((current) => [...current, ...uploaded])
    setPendingFiles([])
  }

  const removeAttachment = async (attachmentId) => {
    try {
      await deleteApprovalAttachment(approvalId, attachmentId)
      setAttachments((current) => current.filter((file) => file.attachmentId !== attachmentId))
      setFeedback('첨부파일을 삭제했습니다.')
    } catch (error) {
      setFeedback(error.response?.data?.message ?? '첨부파일을 삭제하지 못했습니다.')
    }
  }

  const toggleApprover = (userId) => {
    if (isCorrection) {
      setApproverIds((current) => current.includes(userId) ? [] : [userId])
      setFeedback('')
      return
    }
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

    if (isCorrection) {
      if (!title.trim() || !correctionReason.trim() || !correctionCheckIn || approverIds.length !== 1
          || !correctionRecord || correctionRecord.date !== correctionDate) {
        setFeedback('제목, 대상 날짜의 기존 기록, 출근 시각, 정정 사유와 결재자 한 명을 확인해 주세요.')
        return
      }
      try {
        setIsSubmitting(true)
        setFeedback('근태 정정 문서와 첨부파일을 상신하는 중입니다.')
        const result = await submitCorrectionDocument(title.trim(), {
          date: correctionDate, checkInAt: correctionCheckIn, checkOutAt: correctionCheckOut || null,
          reason: correctionReason.trim(), approverId: approverIds[0],
          expectedAttendanceId: correctionRecord.attendanceId, expectedVersion: correctionRecord.version,
        }, pendingFiles)
        navigate(`/approvals/${result.approvalId}`, { replace: true })
      } catch (error) {
        setFeedback(error.response?.data?.message ?? '근태 정정 문서를 상신하지 못했습니다.')
      } finally { setIsSubmitting(false) }
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

      await uploadPendingFiles(targetApprovalId)
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
    if (isCorrection) return
    if (!title.trim() || !content.trim()) {
      setFeedback('제목과 내용을 입력해 주세요.')
      return
    }

    try {
      setIsSaving(true)
      setFeedback('임시저장 중입니다.')

      if (isEditing) {
        await updateDraft(approvalId, title.trim(), content.trim())
        await uploadPendingFiles(approvalId)
        setFeedback('임시저장 문서를 수정했습니다.')
        return
      }

      const createdApprovalId = await createDraft(
        title.trim(),
        content.trim(),
      )

      await uploadPendingFiles(createdApprovalId)

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
                  disabled={isSaving || isSubmitting}
                  onChange={(event) => {
                    setDocumentType(event.target.value)
                    setApproverIds([])
                    setCcUserIds([])
                    setCorrectionRecord(null)
                    setCorrectionLoadError('')
                    setFeedback('')
                  }}
                  value={documentType}
                >
                  {documentTypes.map((type) => (
                    <option key={type} value={type} disabled={isEditing && type === '근태 정정'}>
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

            {isCorrection ? <div className="attendance-correction-form">
              <label className="form-field"><span>정정 대상 날짜</span>
                <input type="date" required value={correctionDate} disabled={isSubmitting}
                  onChange={(event) => { setCorrectionDate(event.target.value); setCorrectionRecord(null); setCorrectionLoadError('') }} />
              </label>
              {correctionLoadError && <p role="alert" className="compose-feedback">{correctionLoadError}</p>}
              {correctionDate && <button type="button" className="compose-button compose-button--secondary" disabled={isSubmitting}
                onClick={() => { setCorrectionRecord(null); setCorrectionLoadError(''); setCorrectionReload((value) => value + 1) }}>기존 기록 다시 조회</button>}
              {correctionRecord?.date === correctionDate ? <>
                <p>기존 출근: {correctionRecord.checkInAt?.replace('T', ' ').slice(0, 19) || '미기록'}<br />
                  기존 퇴근: {correctionRecord.checkOutAt?.replace('T', ' ').slice(0, 19) || '미기록'}</p>
                <label className="form-field"><span>변경할 출근 시각</span><input type="datetime-local" step="1" required
                  value={correctionCheckIn} disabled={isSubmitting} onChange={(event) => setCorrectionCheckIn(event.target.value)} /></label>
                <label className="form-field"><span>변경할 퇴근 시각 (공란은 미기록)</span><input type="datetime-local" step="1"
                  value={correctionCheckOut} disabled={isSubmitting} onChange={(event) => setCorrectionCheckOut(event.target.value)} /></label>
              </> : correctionDate && !correctionLoadError && <p className="compose-feedback">기존 근태를 불러오는 중입니다.</p>}
              <label className="form-field"><span>정정 사유</span><textarea required maxLength={1000} value={correctionReason}
                disabled={isSubmitting} onChange={(event) => setCorrectionReason(event.target.value)} placeholder="출퇴근 시각을 정정하는 사유를 입력하세요." />
                <small>{correctionReason.length} / 1000</small></label>
            </div> : <label className="form-field">
              <span className="sr-only">결재 내용</span>
              <textarea
                onChange={(event) => setContent(event.target.value)}
                placeholder="결재 내용을 입력하세요."
                value={content}
              />
              <small>{content.length.toLocaleString()}자</small>
            </label>}
          </section>

          <section className="panel compose-panel attachment-compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">03</span>
              <div><h2>첨부파일</h2><p>PDF, PNG, JPG, DOCX, XLSX 파일을 최대 5개까지 등록합니다.</p></div>
            </div>
            <label className="attachment-picker">
              <input accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx" multiple onChange={handleFileSelection} type="file" />
              <span>파일 선택</span><small>파일당 20MB · 총 100MB</small>
            </label>
            <div className="attachment-edit-list">
              {attachments.map((file) => (
                <div key={file.attachmentId}><span>{file.originalFileName}</span><button onClick={() => removeAttachment(file.attachmentId)} type="button">삭제</button></div>
              ))}
              {pendingFiles.map((file, index) => (
                <div key={`${file.name}-${file.lastModified}-${index}`}><span>{file.name} · 업로드 대기</span><button onClick={() => setPendingFiles((current) => current.filter((_, itemIndex) => itemIndex !== index))} type="button">제외</button></div>
              ))}
            </div>
          </section>
        </div>

        <aside className="compose-side">
          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">03</span>
              <div>
                <h2>결재선</h2>
                <p>{isCorrection ? '인사 담당자 또는 시스템 관리자 한 명을 선택합니다. 본인은 제외됩니다.' : '승인 순서대로 결재자를 선택합니다.'}</p>
              </div>
            </div>

            {!isCorrection && isDirectoryLoading && (
              <p className="compose-feedback">
                결재자 후보를 불러오는 중입니다.
              </p>
            )}
            {!isCorrection && directoryLoadError && (
              <p className="compose-feedback">{directoryLoadError}</p>
            )}
            {isCorrection && correctionApproversError && <p className="compose-feedback">{correctionApproversError}</p>}
            {(isCorrection ? !correctionApproversError : !isDirectoryLoading && !directoryLoadError) &&
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
                      disabled={isSubmitting}
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
                      {isCorrection ? <small>근태 정정 결재자</small> : <small>
                        {approver.departmentName ?? '부서 미배정'} ·{' '}
                        {approver.positionName ?? '직급 미배정'}
                      </small>}
                    </span>
                  </label>
                )
              })}
            </div>
          </section>

          {!isCorrection && <section className="panel compose-panel">
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
          </section>}
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
            disabled={isCorrection || isSaving || isSubmitting || isDeleting}
            title={isCorrection ? '근태 정정은 임시저장 없이 바로 상신합니다.' : undefined}
            onClick={handleSaveDraft}
            type="button"
          >
            {isSaving ? '저장 중...' : '임시저장'}
          </button>
          {isCorrection && <small>근태 정정은 바로 상신합니다.</small>}
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
