import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

let refreshPromise = null

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
  sessionStorage.removeItem('accessToken')

  if (window.location.pathname !== '/login') {
    window.location.replace('/login')
  }
}

async function refreshAccessToken() {
  const response = await axios.post(`${API_BASE_URL}/auth/refresh`, null, {
    withCredentials: true,
    headers: {
      'Content-Type': 'application/json',
    },
  })

  const newAccessToken = response.data.accessToken
  sessionStorage.setItem('accessToken', newAccessToken)

  return newAccessToken
}

apiClient.interceptors.request.use(
  (config) => {
    const accessToken = sessionStorage.getItem('accessToken')

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
    const currentAccessToken = sessionStorage.getItem('accessToken')
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
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null
        })
      }

      const newAccessToken = await refreshPromise
      originalRequest.headers = originalRequest.headers ?? {}
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`

      return apiClient(originalRequest)
    } catch (refreshError) {
      redirectToLogin()
      return Promise.reject(refreshError)
    }
  },
)

export default apiClient
