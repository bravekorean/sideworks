import apiClient from './apiClient'

export async function getAttendanceManagementScope() {
  const response = await apiClient.get('/attendances/management/scope')
  return response.data
}

export async function getManagedAttendances(params) {
  const response = await apiClient.get('/attendances/management', { params })
  return response.data
}
