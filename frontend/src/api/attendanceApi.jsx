import apiClient from './apiClient'

export async function checkIn() {
  const response = await apiClient.post('/attendances/check-in')

  return response.data
}

export async function checkOut() {
  const response = await apiClient.post('/attendances/check-out')

  return response.data
}

export async function getTodayAttendance() {
  const response = await apiClient.get('/attendances/me/today')

  return response.data
}

export async function getMonthlyAttendances(year, month) {
  const response = await apiClient.get('/attendances/me', {
    params: { year, month },
  })

  return response.data
}
