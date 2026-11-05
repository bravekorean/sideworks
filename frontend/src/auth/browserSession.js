import axios from 'axios'

const CHANGE_KEY = 'sideworks.auth-change'
const CHANGE_EVENT = 'sideworks:auth-change'
let refreshPromise = null
let generation = 0

export function getAccessToken() {
  return sessionStorage.getItem('accessToken')
}

export function invalidateAuthentication() {
  generation += 1
  refreshPromise = null
  sessionStorage.removeItem('accessToken')
}

export function isAuthenticationError(error) {
  return error.response?.status === 401 || error.response?.status === 403
}

export function accessTokenSubject(token) {
  try {
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(window.atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '='))).sub
  } catch {
    return null
  }
}

// 같은 브라우저에서 로그인·로그아웃·재발급이 쿠키를 서로 덮어쓰지 않도록 순서를 맞춘다.
function withAuthLock(action) {
  return window.navigator.locks
    ? window.navigator.locks.request('sideworks-auth', action)
    : action()
}

function authRequest(path, data = null) {
  return axios.post(`/api/auth/${path}`, data, { withCredentials: true, timeout: 10000 })
}

function publishChange(kind) {
  const change = { kind, id: window.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}` }
  try {
    // 토큰은 저장하지 않는다. 다른 탭은 쿠키로 인증을 다시 확인한다.
    localStorage.setItem(CHANGE_KEY, JSON.stringify(change))
  } catch {
    // 저장소 사용이 제한된 브라우저에서는 다시 활성화될 때 쿠키로 확인한다.
  }
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: change }))
}

export function subscribeAuthentication(listener) {
  const local = (event) => listener(event.detail, false)
  const remote = (event) => {
    if (event.key !== CHANGE_KEY || !event.newValue) return
    try {
      const change = JSON.parse(event.newValue)
      if (['login', 'logout'].includes(change.kind)) listener(change, true)
    } catch {
      // 다른 코드나 확장이 기록한 잘못된 값은 인증 상태로 사용하지 않는다.
    }
  }
  window.addEventListener(CHANGE_EVENT, local)
  window.addEventListener('storage', remote)
  return () => {
    window.removeEventListener(CHANGE_EVENT, local)
    window.removeEventListener('storage', remote)
  }
}

export function refreshAccessToken() {
  if (!refreshPromise) {
    const startedAt = generation
    const request = withAuthLock(async () => {
      if (startedAt !== generation) throw new Error('인증 상태가 변경되었습니다.')
      const response = await authRequest('refresh')
      if (startedAt !== generation) throw new Error('인증 상태가 변경되었습니다.')
      sessionStorage.setItem('accessToken', response.data.accessToken)
      return response.data.accessToken
    })
    refreshPromise = request
    request.finally(() => {
      if (refreshPromise === request) refreshPromise = null
    }).catch(() => {})
  }
  return refreshPromise
}

export async function restoreAuthentication() {
  try {
    await refreshAccessToken()
    return true
  } catch (error) {
    if (!isAuthenticationError(error)) throw error
    invalidateAuthentication()
    return false
  }
}

export function loginWithBrowserSession(loginId, password) {
  return withAuthLock(async () => {
    let response
    try {
      // 다른 탭이 먼저 로그인했다면 기존 계정을 유지한다.
      response = await authRequest('refresh')
    } catch (error) {
      if (!isAuthenticationError(error)) throw error
      response = await authRequest('login', { loginId, password })
    }
    invalidateAuthentication()
    sessionStorage.setItem('accessToken', response.data.accessToken)
    publishChange('login')
    return response.data
  })
}

export function logoutBrowserSession() {
  return withAuthLock(async () => {
    // 쿠키 삭제에 실패하면 로그아웃이 완료된 것처럼 표시하지 않는다.
    await authRequest('logout')
    endBrowserSession()
  })
}

export function endBrowserSession() {
  invalidateAuthentication()
  publishChange('logout')
}
