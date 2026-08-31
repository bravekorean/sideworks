import apiClient from './apiClient'

const approvalBoxEndpoints = {
  drafts: '/approvals/drafts',
  sent: '/approvals/sent',
  pending: '/approvals/pending',
  processed: '/approvals/processed',
  cc: '/approvals/cc',
}

export async function getApprovalBox(
  box,
  { page = 0, size = 5, keyword = '', status = '' } = {},
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

export async function createDraft(title, content) {
  const response = await apiClient.post('/approvals', {
    title,
    content,
  })

  return response.data.approvalId
}

export async function updateDraft(approvalId, title, content) {
  await apiClient.put(`/approvals/${approvalId}`, {
    title,
    content,
  })
}

export async function getApprovalDetail(approvalId) {
  const response = await apiClient.get(`/approvals/${approvalId}`)

  return response.data
}

export async function submitApproval(approvalId, approverIds, ccUserIds) {
  await apiClient.post(`/approvals/${approvalId}/submit`, {
    approverIds,
    ccUserIds,
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

export async function deleteDraft(approvalId) {
  await apiClient.delete(`/approvals/${approvalId}`)
}
