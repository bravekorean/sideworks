import apiClient from './apiClient'

export async function login(loginId, password) {
  const response = await apiClient.post('/auth/login', {
    loginId,
    password,
  })

  return response.data
}

export async function logout() {
  await apiClient.post('/auth/logout')
}