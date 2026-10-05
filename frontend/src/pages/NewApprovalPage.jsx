import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  createDraft,
  deleteApprovalAttachment,
  deleteDraft,
  getApprovalDetail,
  getApprovalDocumentTypes,
  submitApproval,
  uploadApprovalAttachments,
  updateDraft,
} from '../api/approvalApi'
import { getDirectory } from '../api/userApi'
import { getCorrectionApprovers, getCorrectionRecord, submitCorrectionDocument } from '../api/attendanceCorrectionApi'
import {
  getCancelableLeaveRequests,
  getMyAnnualLeaveAvailability,
  submitLeaveCancellationDocument,
  submitLeaveRequestDocument,
} from '../api/annualLeaveApi'
import { getAvailableTemplates, resolveTemplate } from '../api/approvalTemplateApi'

function NewApprovalPage() {
  const { approvalId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const isEditing = Boolean(approvalId)

  // 결재 문서 입력 상태
  const [documentTypes, setDocumentTypes] = useState([])
  const [documentType, setDocumentType] = useState('')
  const [savedTypeName, setSavedTypeName] = useState('')
  const requestedCorrection = searchParams.get('type') === 'attendance-correction'
  const requestedLeave = searchParams.get('type') === 'leave-request'
  const requestedLeaveCancellation = searchParams.get('type') === 'leave-cancellation'
  const selectedType = documentTypes.find((type) => String(type.approvalDocumentTypeId) === documentType)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [approverIds, setApproverIds] = useState([])
  const [ccUserIds, setCcUserIds] = useState([])
  const [templates, setTemplates] = useState([])
  const [templateSelection, setTemplateSelection] = useState(null)
  const [templateMembers, setTemplateMembers] = useState({ approvers: [], ccUsers: [] })
  const [templateError, setTemplateError] = useState('')
  const [attachments, setAttachments] = useState([])
  const [pendingFiles, setPendingFiles] = useState([])
  const isCorrection = selectedType?.behaviorType === 'ATTENDANCE_CORRECTION'
  const isLeaveRequest = selectedType?.behaviorType === 'LEAVE_REQUEST'
  const isLeaveCancellation = selectedType?.behaviorType === 'LEAVE_CANCELLATION'
  const canUseSelectedType = selectedType?.behaviorType === 'GENERAL'
    || (!isEditing && (isCorrection || isLeaveRequest || isLeaveCancellation))
  const [correctionDate, setCorrectionDate] = useState(searchParams.get('date') || '')
  const [correctionRecord, setCorrectionRecord] = useState(null)
  const [correctionCheckIn, setCorrectionCheckIn] = useState('')
  const [correctionCheckOut, setCorrectionCheckOut] = useState('')
  const [correctionReason, setCorrectionReason] = useState('')
  const [correctionApprovers, setCorrectionApprovers] = useState([])
  const [correctionLoadError, setCorrectionLoadError] = useState('')
  const [correctionApproversError, setCorrectionApproversError] = useState('')
  const [correctionReload, setCorrectionReload] = useState(0)
  const [leaveStartDate, setLeaveStartDate] = useState('')
  const [leaveEndDate, setLeaveEndDate] = useState('')
  const [leavePeriod, setLeavePeriod] = useState('FULL')
  const [leaveBalance, setLeaveBalance] = useState(null)
  const [leaveBalanceError, setLeaveBalanceError] = useState('')
  const [leaveBalanceReload, setLeaveBalanceReload] = useState(0)
  const [cancelableLeaves, setCancelableLeaves] = useState([])
  const [selectedLeaveRequestId, setSelectedLeaveRequestId] = useState('')
  const [cancelableLeavesError, setCancelableLeavesError] = useState('')
  const leaveYear = isLeaveRequest && leaveStartDate ? Number(leaveStartDate.slice(0, 4)) : null

  useEffect(() => {
    let active = true
    getAvailableTemplates(0, 100).then((page) => {
      if (active) { setTemplates(page.content); setTemplateError('') }
    }).catch((error) => {
      if (active) setTemplateError(error.response?.data?.message ?? '결재선 템플릿을 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!leaveYear) return undefined
    let active = true
    getMyAnnualLeaveAvailability(leaveYear)
      .then((balance) => { if (active) setLeaveBalance(balance) })
      .catch((error) => {
        if (active) setLeaveBalanceError(error.response?.data?.message ?? '연차 잔액을 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [leaveYear, leaveBalanceReload])

  useEffect(() => {
    if (!isLeaveCancellation) return undefined
    let active = true
    getCancelableLeaveRequests().then((items) => {
      if (active) { setCancelableLeaves(items); setCancelableLeavesError('') }
    }).catch((error) => {
      if (active) setCancelableLeavesError(error.response?.data?.message ?? '취소 가능한 휴가를 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [isLeaveCancellation])

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
  const [isDraftLoading, setIsDraftLoading] = useState(true)
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
    let isActive = true

    const loadDraft = async () => {
      try {
        setIsDraftLoading(true)
        setDraftLoadError('')

        const [types, approval] = await Promise.all([
          getApprovalDocumentTypes(),
          isEditing ? getApprovalDetail(approvalId) : Promise.resolve(null),
        ])

        if (!isActive) {
          return
        }

        setDocumentTypes(types)
        if (!approval) {
          const initialType = requestedCorrection
            ? types.find((type) => type.behaviorType === 'ATTENDANCE_CORRECTION')
            : requestedLeave
              ? types.find((type) => type.behaviorType === 'LEAVE_REQUEST')
            : requestedLeaveCancellation
              ? types.find((type) => type.behaviorType === 'LEAVE_CANCELLATION')
            : types.find((type) => type.typeCode === 'GENERAL_PROPOSAL')
              ?? types.find((type) => type.behaviorType === 'GENERAL')
          setDocumentType(initialType ? String(initialType.approvalDocumentTypeId) : '')
          setContent(initialType?.contentTemplate ?? '')
          return
        }
        if (approval.approvalStatus !== 'DRAFT') {
          setDraftLoadError('이미 상신되었거나 수정할 수 없는 문서입니다.')
          return
        }

        setTitle(approval.title)
        setContent(approval.content)
        setDocumentType(approval.documentTypeId == null ? '' : String(approval.documentTypeId))
        setSavedTypeName(approval.documentTypeName ?? '')
        setAttachments(approval.attachments ?? [])
      } catch (error) {
        if (isActive) {
          setDraftLoadError(
            error.response?.data?.message ??
              '문서 종류 또는 임시저장 문서를 불러오지 못했습니다.',
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
  }, [approvalId, isEditing, requestedCorrection, requestedLeave, requestedLeaveCancellation])

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

  const availableApprovers = isCorrection ? correctionApprovers : [...directoryUsers,
    ...templateMembers.approvers.filter((member) => !directoryUsers.some((user) => user.userId === member.userId))]
  const availableCcUsers = [...directoryUsers, ...templateMembers.ccUsers.filter(
    (member) => !directoryUsers.some((user) => user.userId === member.userId),
  )]

  const applyTemplate = async (id) => {
    if (!id) {
      setTemplateSelection(null)
      setTemplateMembers({ approvers: [], ccUsers: [] })
      setFeedback('수동 결재선으로 전환했습니다. 현재 선택한 결재자·참조자는 유지됩니다.')
      return
    }
    const selected = templates.find((item) => String(item.approvalTemplateId) === id)
    if (!selected || selected.validationStatus !== 'VALID') return
    if ((approverIds.length || ccUserIds.length) && !window.confirm('현재 결재선이 템플릿 구성으로 교체됩니다. 계속할까요?')) return
    try {
      const resolved = await resolveTemplate(Number(id))
      setApproverIds(resolved.approverIds)
      setCcUserIds(resolved.ccUserIds)
      setTemplateSelection({ id: resolved.templateId, version: resolved.version })
      setTemplateMembers({ approvers: resolved.approvers, ccUsers: resolved.ccUsers })
      setFeedback(resolved.authorIsApprover
        ? '작성자가 결재자에 포함돼 있습니다. 상신 전 결재자를 수동 변경해주세요.'
        : '템플릿 결재선을 불러왔습니다. 상신 전 직접 조정할 수 있습니다.')
    } catch (error) {
      setFeedback(error.response?.data?.message ?? '템플릿을 적용하지 못했습니다.')
      getAvailableTemplates(0, 100).then((page) => setTemplates(page.content)).catch(() => {})
    }
  }
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

  const moveSelectedApprover = (index, direction) => {
    setApproverIds((current) => {
      const target = index + direction
      if (target < 0 || target >= current.length) return current
      const ordered = [...current]
      ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
      return ordered
    })
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

  const handleDocumentTypeChange = (event) => {
    const nextType = documentTypes.find((type) => String(type.approvalDocumentTypeId) === event.target.value)
    if (!nextType || event.target.value === documentType) return
    if ((content.trim() || correctionReason.trim() || correctionCheckIn || correctionCheckOut || leaveStartDate)
        && !window.confirm('문서 종류를 바꾸면 본문 또는 정정 입력 내용이 새 양식으로 교체됩니다. 계속할까요?')) return
    setDocumentType(event.target.value)
    setContent(nextType.contentTemplate ?? '')
    setApproverIds([])
    setCcUserIds([])
    setTemplateSelection(null)
    setTemplateMembers({ approvers: [], ccUsers: [] })
    setCorrectionRecord(null)
    setCorrectionReason('')
    setCorrectionCheckIn('')
    setCorrectionCheckOut('')
    setCorrectionLoadError('')
    setLeaveStartDate('')
    setLeaveEndDate('')
    setLeavePeriod('FULL')
    setLeaveBalance(null)
    setLeaveBalanceError('')
    setSelectedLeaveRequestId('')
    setFeedback('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (isSaving || isSubmitting) {
      return
    }

    if (!canUseSelectedType) {
      setFeedback('사용 가능한 문서 종류를 선택해 주세요.')
      return
    }

    if (isLeaveCancellation) {
      if (!selectedLeaveRequestId || !content.trim() || approverIds.length === 0) {
        setFeedback('취소할 승인 휴가·취소 사유·결재선을 확인해 주세요.')
        return
      }
      try {
        setIsSubmitting(true)
        setFeedback('휴가 취소 문서를 상신하는 중입니다.')
        const result = await submitLeaveCancellationDocument({
          leaveRequestId: Number(selectedLeaveRequestId), reason: content.trim(),
          approverIds, ccUserIds, templateId: templateSelection?.id ?? null,
          templateVersion: templateSelection?.version ?? null,
        }, pendingFiles)
        navigate(`/approvals/${result.approvalId}`, { replace: true })
      } catch (error) {
        setFeedback(error.response?.data?.message ?? '휴가 취소 문서를 상신하지 못했습니다.')
      } finally { setIsSubmitting(false) }
      return
    }

    if (isLeaveRequest) {
      if (!leaveStartDate || !leaveEndDate || !content.trim() || approverIds.length === 0
          || !leaveBalance || leaveBalance.leaveYear !== Number(leaveStartDate.slice(0, 4))) {
        setFeedback('휴가 기간·사유·연차 잔액과 결재선을 확인해 주세요.')
        return
      }
      if (leavePeriod !== 'FULL' && leaveStartDate !== leaveEndDate) {
        setFeedback('반차는 하루만 신청할 수 있습니다.')
        return
      }
      try {
        setIsSubmitting(true)
        setFeedback('휴가 신청 문서와 첨부파일을 상신하는 중입니다.')
        const result = await submitLeaveRequestDocument({
          startDate: leaveStartDate, endDate: leaveEndDate,
          period: leavePeriod, reason: content.trim(), approverIds, ccUserIds,
          templateId: templateSelection?.id ?? null, templateVersion: templateSelection?.version ?? null,
        }, pendingFiles)
        navigate(`/approvals/${result.approvalId}`, { replace: true })
      } catch (error) {
        setFeedback(error.response?.data?.message ?? '휴가 신청 문서를 상신하지 못했습니다.')
      } finally { setIsSubmitting(false) }
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
          Number(documentType),
        )
      } else {
        targetApprovalId = await createDraft(
          title.trim(),
          content.trim(),
          Number(documentType),
        )

        // 상신에 실패해도 생성된 임시저장을 다시 수정할 수 있게 URL을 보존한다.
        navigate(`/approvals/${targetApprovalId}/edit`, {
          replace: true,
        })
      }

      await uploadPendingFiles(targetApprovalId)
      await submitApproval(targetApprovalId, approverIds, ccUserIds,
        templateSelection?.id ?? null, templateSelection?.version ?? null)

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
    if (isCorrection || isLeaveRequest || isLeaveCancellation || isSaving || isSubmitting || isDeleting) return
    if (!canUseSelectedType) {
      setFeedback('사용 가능한 문서 종류를 선택해 주세요.')
      return
    }
    if (!title.trim() || !content.trim()) {
      setFeedback('제목과 내용을 입력해 주세요.')
      return
    }

    try {
      setIsSaving(true)
      setFeedback('임시저장 중입니다.')

      if (isEditing) {
        await updateDraft(approvalId, title.trim(), content.trim(), Number(documentType))
        await uploadPendingFiles(approvalId)
        setFeedback('임시저장 문서를 수정했습니다.')
        return
      }

      const createdApprovalId = await createDraft(
        title.trim(),
        content.trim(),
        Number(documentType),
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
          <p>문서 종류와 작성 양식을 불러오는 중입니다.</p>
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
                  onChange={handleDocumentTypeChange}
                  value={documentType}
                >
                  <option value="" disabled>문서 종류를 선택해 주세요</option>
                  {documentType && !selectedType && (
                    <option value={documentType} disabled>{savedTypeName || '기존 문서 종류'} (사용 불가)</option>
                  )}
                  {documentTypes.map((type) => (
                    <option key={type.approvalDocumentTypeId} value={String(type.approvalDocumentTypeId)}
                      disabled={type.behaviorType !== 'GENERAL' && (isEditing || !['ATTENDANCE_CORRECTION', 'LEAVE_REQUEST', 'LEAVE_CANCELLATION'].includes(type.behaviorType))}>
                      {type.typeName}
                    </option>
                  ))}
                </select>
                {!canUseSelectedType && <small>활성화된 사용 가능한 종류를 선택해야 저장·상신할 수 있습니다.</small>}
              </label>

              <label className="form-field form-field--wide">
                <span>{isLeaveRequest || isLeaveCancellation ? '제목 (상신 시 자동 생성)' : '제목'}</span>
                <input
                  disabled={isLeaveRequest || isLeaveCancellation}
                  maxLength={200}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder={isLeaveRequest ? '[휴가 신청] 이름 / 시작일~종료일'
                    : isLeaveCancellation ? '[휴가 취소] 이름 / 시작일~종료일' : '결재 문서 제목을 입력하세요.'}
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

            {isLeaveCancellation ? <div className="leave-request-form">
              <p className="compose-feedback">휴가 시작 전 승인된 휴가만 전체 취소할 수 있습니다. 취소 결재가 승인되면 원래 연도 잔액으로 복원됩니다.</p>
              {cancelableLeavesError && <p role="alert" className="compose-feedback">{cancelableLeavesError}</p>}
              <label className="form-field"><span>취소할 승인 휴가</span>
                <select value={selectedLeaveRequestId} onChange={(event) => setSelectedLeaveRequestId(event.target.value)}>
                  <option value="">승인된 휴가를 선택해 주세요</option>
                  {cancelableLeaves.map((item) => <option key={item.leaveRequestId} value={item.leaveRequestId}>
                    {item.startDate} ~ {item.endDate} · {item.totalDays}일
                  </option>)}
                </select>
              </label>
              {!cancelableLeavesError && cancelableLeaves.length === 0 && <p>취소 가능한 승인 휴가가 없습니다.</p>}
              <label className="form-field"><span>취소 사유</span><textarea value={content}
                onChange={(event) => setContent(event.target.value)} placeholder="전체 취소 사유를 입력하세요." /></label>
            </div> : isLeaveRequest ? <div className="leave-request-form">
              <p className="compose-feedback">휴무일은 신청 일수에서 제외됩니다. 상신 시 서버가 사용 가능량과 날짜 중복을 확인합니다.</p>
              <div className="leave-request-form__dates">
                <label className="form-field"><span>시작일</span><input type="date" value={leaveStartDate}
                  onChange={(event) => {
                    if (event.target.value.slice(0, 4) !== leaveStartDate.slice(0, 4)) {
                      setLeaveBalance(null)
                      setLeaveBalanceError('')
                    }
                    setLeaveStartDate(event.target.value)
                    setLeaveEndDate(event.target.value)
                  }} /></label>
                <label className="form-field"><span>종료일</span><input type="date" min={leaveStartDate || undefined}
                  value={leaveEndDate} onChange={(event) => setLeaveEndDate(event.target.value)} /></label>
                <label className="form-field"><span>휴가 단위</span><select value={leavePeriod}
                  onChange={(event) => setLeavePeriod(event.target.value)}>
                  <option value="FULL">연차 (1일)</option>
                  <option value="AM">오전 반차 (0.5일)</option>
                  <option value="PM">오후 반차 (0.5일)</option>
                </select></label>
              </div>
              {leaveStartDate && <div className="leave-request-form__balance" aria-live="polite">
                {!leaveBalance && !leaveBalanceError && <span>연차 잔액을 불러오는 중입니다.</span>}
                {leaveBalanceError && <span role="alert">{leaveBalanceError}</span>}
                {leaveBalanceError && <button className="compose-button compose-button--secondary"
                  onClick={() => { setLeaveBalanceError(''); setLeaveBalanceReload((value) => value + 1) }}
                  type="button">다시 조회</button>}
                {leaveBalance?.leaveYear === leaveYear && <>
                  <span>{leaveBalance.leaveYear}년 부여 <strong>{leaveBalance.grantedDays}일</strong></span>
                  <span>잔여 <strong>{leaveBalance.remainingDays}일</strong></span>
                  <span>승인 대기 <strong>{leaveBalance.pendingDays}일</strong></span>
                  <span>신청 가능 <strong>{leaveBalance.availableDays}일</strong></span>
                  <small>상신·승인 시 서버가 잔액과 다른 신청을 다시 검증합니다.</small>
                </>}
              </div>}
              <label className="form-field"><span>신청 사유</span><textarea value={content}
                onChange={(event) => setContent(event.target.value)} placeholder="휴가 신청 사유를 입력하세요." /></label>
            </div> : isCorrection ? <div className="attendance-correction-form">
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
                <p>{isCorrection ? '인사 담당자 또는 시스템 관리자 한 명을 선택합니다. 본인은 제외됩니다.'
                  : '승인 순서대로 결재자를 선택합니다. 휴가도 다단계 결재가 가능합니다.'}</p>
              </div>
            </div>

            {!isCorrection && <label className="form-field">
              <span>결재선 템플릿 (선택)</span>
              <select disabled={isSubmitting} value={templateSelection?.id ?? ''}
                onChange={(event) => applyTemplate(event.target.value)}>
                <option value="">수동 지정</option>
                {templates.map((template) => <option key={template.approvalTemplateId}
                  disabled={template.validationStatus !== 'VALID'} value={template.approvalTemplateId}>
                  {template.templateName} · {template.scope === 'COMMON' ? '공용' : template.departmentName}
                  {template.validationStatus !== 'VALID' ? ' (인사정보 변경 · 사용 불가)' : ''}
                </option>)}
              </select>
              {templateError && <small role="alert">{templateError}</small>}
              {templateSelection && <small>원본 버전 {templateSelection.version} · 아래에서 결재자와 참조자를 조정할 수 있습니다.</small>}
              {templates.some((template) => template.validationStatus !== 'VALID') && <small>인사정보가 변경된 템플릿은 관리자가 수정하기 전까지 적용할 수 없습니다.</small>}
            </label>}

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
            {!isCorrection && approverIds.length > 0 &&
              <div className="template-ordered-list"><strong>현재 결재 순서</strong>
                <ol>{approverIds.map((id, index) => <li key={id}>
                  <span>{index + 1}. {availableApprovers.find((user) => user.userId === id)?.userName ?? `사용자 ${id}`}</span>
                  <button type="button" disabled={index === 0 || isSubmitting}
                    onClick={() => moveSelectedApprover(index, -1)}>↑</button>
                  <button type="button" disabled={index === approverIds.length - 1 || isSubmitting}
                    onClick={() => moveSelectedApprover(index, 1)}>↓</button>
                </li>)}</ol>
              </div>}
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
            disabled={!canUseSelectedType || isCorrection || isLeaveRequest || isLeaveCancellation || isSaving || isSubmitting || isDeleting}
            title={isCorrection || isLeaveRequest || isLeaveCancellation ? '전용 신청은 임시저장 없이 바로 상신합니다.' : undefined}
            onClick={handleSaveDraft}
            type="button"
          >
            {isSaving ? '저장 중...' : '임시저장'}
          </button>
          {(isCorrection || isLeaveRequest || isLeaveCancellation) && <small>전용 신청은 바로 상신합니다.</small>}
          <button
            className="compose-button compose-button--primary"
            disabled={!canUseSelectedType || isSaving || isSubmitting || isDeleting}
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
