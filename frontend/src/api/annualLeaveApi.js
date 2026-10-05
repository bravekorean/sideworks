import apiClient from './apiClient'

export async function getMyAnnualLeaveAvailability(year) {
  const response = await apiClient.get('/annual-leave/me/availability', {
    params: { year },
  })
  return response.data
}

export async function submitLeaveRequestDocument(request, files) {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  files.forEach((file) => formData.append('files', file))
  const response = await apiClient.post('/annual-leave/requests', formData)
  return response.data
}

export async function getCancelableLeaveRequests() {
  const response = await apiClient.get('/annual-leave/requests/approved')
  return response.data
}

export async function submitLeaveCancellationDocument(request, files) {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  files.forEach((file) => formData.append('files', file))
  const response = await apiClient.post('/annual-leave/requests/cancellations', formData)
  return response.data
}

export async function getMyApprovedLeaveDays(year, month) {
  const response = await apiClient.get('/annual-leave/me/days', { params: { year, month } })
  return response.data
}
