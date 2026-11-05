import axios from 'axios'
import { getAccessToken, invalidateAuthentication, isAuthenticationError, refreshAccessToken } from '../auth/browserSession'

const API_BASE_URL = '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

function isAuthRequest(url = '') {
  return ['/auth/login', '/auth/refresh', '/auth/logout'].some((path) =>
    url.includes(path),
  )
}

function isAccessTokenExpired(accessToken) {
  if (!accessToken) {
    return false
  }

  try {
    const encodedPayload = accessToken.split('.')[1]
    const normalizedPayload = encodedPayload
      .replace(/-/g, '+')
      .replace(/_/g, '/')
    const base64 = normalizedPayload.padEnd(
      Math.ceil(normalizedPayload.length / 4) * 4,
      '=',
    )
    const payload = JSON.parse(window.atob(base64))

    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()
  } catch {
    return false
  }
}

function redirectToLogin() {
  invalidateAuthentication()

  if (window.location.pathname !== '/login') {
    window.location.replace('/login')
  }
}

// fetch 스트림은 Axios 인터셉터를 통과하지 않으므로 같은 재발급 경로를 재사용한다.
export async function getValidAccessToken(forceRefresh = false) {
  const accessToken = getAccessToken()
  if (!forceRefresh && accessToken && !isAccessTokenExpired(accessToken)) return accessToken
  return refreshAccessToken()
}

apiClient.interceptors.request.use(
  (config) => {
    const accessToken = getAccessToken()

    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }

    return config
  },
  (error) => Promise.reject(error),
)

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const status = error.response?.status
    const currentAccessToken = getAccessToken()
    const shouldRefresh =
      status === 401 ||
      (status === 403 && isAccessTokenExpired(currentAccessToken))

    if (
      !originalRequest ||
      originalRequest._retry ||
      isAuthRequest(originalRequest.url) ||
      !shouldRefresh
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    try {
      const newAccessToken = await refreshAccessToken()
      originalRequest.headers = originalRequest.headers ?? {}
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`

      return apiClient(originalRequest)
    } catch (refreshError) {
      if (isAuthenticationError(refreshError)) redirectToLogin()
      return Promise.reject(refreshError)
    }
  },
)

export default apiClient
