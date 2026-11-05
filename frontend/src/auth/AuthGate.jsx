import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router'
import {
  accessTokenSubject, getAccessToken, invalidateAuthentication,
  restoreAuthentication, subscribeAuthentication,
} from './browserSession'

export default function AuthGate({ children }) {
  const [status, setStatus] = useState('checking')
  const location = useLocation()

  useEffect(() => {
    let active = true
    let checkId = 0
    const check = async () => {
      const id = ++checkId
      const previousUser = accessTokenSubject(getAccessToken())
      try {
        const authenticated = await restoreAuthentication()
        if (!active || id !== checkId) return
        const currentUser = accessTokenSubject(getAccessToken())
        if (authenticated && previousUser && previousUser !== currentUser) {
          // 이전 계정의 화면·요청·SSE를 모두 정리한다.
          window.location.reload()
          return
        }
        setStatus(authenticated ? 'authenticated' : 'anonymous')
      } catch {
        if (active && id === checkId) {
          setStatus((current) => current === 'checking' ? 'error' : current)
        }
      }
    }
    const unsubscribe = subscribeAuthentication((change, remote) => {
      checkId += 1
      if (remote) {
        invalidateAuthentication()
        window.location.reload()
      } else {
        setStatus(change.kind === 'login' ? 'authenticated' : 'anonymous')
      }
    })
    const onVisible = () => {
      if (document.visibilityState === 'visible') void check()
    }
    const onPageShow = (event) => { if (event.persisted) void check() }
    void check()
    window.addEventListener('focus', check)
    window.addEventListener('pageshow', onPageShow)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      active = false
      unsubscribe()
      window.removeEventListener('focus', check)
      window.removeEventListener('pageshow', onPageShow)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  if (status === 'checking' || status === 'error') {
    return (
      <main className="auth-status" role="status">
        <p>{status === 'checking' ? '로그인 상태를 확인하고 있습니다.' : '서버에 연결할 수 없습니다. 연결을 확인한 뒤 다시 시도해 주세요.'}</p>
        {status === 'error' && <button type="button" onClick={() => window.location.reload()}>다시 시도</button>}
      </main>
    )
  }
  if (status === 'anonymous' && location.pathname !== '/login') {
    return <Navigate to="/login" replace />
  }
  if (status === 'authenticated' && location.pathname === '/login') {
    return <Navigate to="/dashboard" replace />
  }
  return children
}
