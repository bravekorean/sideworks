import apiClient from './apiClient'

const approvalBoxEndpoints = {
  drafts: '/approvals/drafts',
  sent: '/approvals/sent',
  pending: '/approvals/pending',
  processed: '/approvals/processed',
  cc: '/approvals/cc',
  manage: '/approvals/manage',
}

export async function getApprovalBox(
  box,
  { page = 0, size = 5, keyword = '', status = '', blockedOnly = false } = {},
) {
  const endpoint = approvalBoxEndpoints[box]

  if (!endpoint) {
    throw new Error(`지원하지 않는 결재함입니다: ${box}`)
  }

  const response = await apiClient.get(endpoint, {
    params: {
      page,
      size,
      keyword: keyword || undefined,
      status: status || undefined,
      blockedOnly: box === 'manage' ? blockedOnly : undefined,
    },
  })

  return response.data
}

export async function getRecentApprovalActivities({ page = 0, size = 5 } = {}) {
  const response = await apiClient.get('/approvals/activities', {
    params: { page, size },
  })

  return response.data
}

export async function searchApprovals(keyword, { page = 0, size = 20 } = {}) {
  const response = await apiClient.get('/approvals/search', {
    params: { keyword, page, size },
  })

  return response.data
}

export async function getApprovalDocumentTypes() {
  const response = await apiClient.get('/approval-document-types')
  return response.data
}

export async function createDraft(title, content, documentTypeId) {
  const response = await apiClient.post('/approvals', {
    title,
    content,
    documentTypeId,
  })

  return response.data.approvalId
}

export async function updateDraft(approvalId, title, content, documentTypeId) {
  await apiClient.put(`/approvals/${approvalId}`, {
    title,
    content,
    documentTypeId,
  })
}

export async function getApprovalDetail(approvalId) {
  const response = await apiClient.get(`/approvals/${approvalId}`)

  return response.data
}

export async function submitApproval(approvalId, approverIds, ccUserIds, templateId = null, templateVersion = null) {
  await apiClient.post(`/approvals/${approvalId}/submit`, {
    approverIds,
    ccUserIds,
    templateId,
    templateVersion,
  })
}

export async function approveApproval(approvalId, comment) {
  await apiClient.post(`/approvals/${approvalId}/approve`, {
    comment: comment?.trim() || null,
  })
}

export async function rejectApproval(approvalId, comment) {
  await apiClient.post(`/approvals/${approvalId}/reject`, {
    comment: comment.trim(),
  })
}

export async function cancelApproval(approvalId) {
  await apiClient.post(`/approvals/${approvalId}/cancel`)
}

export async function terminateApproval(approvalId, reason) {
  await apiClient.post(`/approvals/${approvalId}/terminate`, { reason: reason.trim() })
}

export async function deleteDraft(approvalId) {
  await apiClient.delete(`/approvals/${approvalId}`)
}

export async function uploadApprovalAttachments(approvalId, files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  const response = await apiClient.post(
    `/approvals/${approvalId}/attachments`,
    formData,
  )
  return response.data
}

export async function deleteApprovalAttachment(approvalId, attachmentId) {
  await apiClient.delete(`/approvals/${approvalId}/attachments/${attachmentId}`)
}

export async function downloadApprovalAttachment(approvalId, attachment) {
  const response = await apiClient.get(
    `/approvals/${approvalId}/attachments/${attachment.attachmentId}/download`,
    { responseType: 'blob' },
  )
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = attachment.originalFileName
  link.click()
  URL.revokeObjectURL(url)
}
