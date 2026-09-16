import apiClient from './apiClient'

export const getCorrectionApprovers = async () => (await apiClient.get('/attendances/corrections/approvers')).data
export const getCorrectionRecord = async (params) => (await apiClient.get('/attendances/corrections/record', { params })).data
export const getCorrectionHistory = async (params) => (await apiClient.get('/attendances/corrections/history', { params })).data
export const submitCorrection = async (data) => (await apiClient.post('/attendances/corrections', data)).data
export async function submitCorrectionDocument(title, correction, files) {
  const form = new FormData()
  form.append('request', new Blob([JSON.stringify({ title, correction })], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return (await apiClient.post('/attendances/corrections/documents', form)).data
}
export const correctAttendanceDirectly = async (userId, data) => (await apiClient.put(`/attendances/corrections/direct/${userId}`, data)).data
export const getScheduleExceptions = async (year) => (await apiClient.get('/admin/work-schedule-exceptions', { params: { year } })).data
export const createScheduleException = async (data) => (await apiClient.post('/admin/work-schedule-exceptions', data)).data
export const updateScheduleException = async (id, data) => (await apiClient.put(`/admin/work-schedule-exceptions/${id}`, data)).data
