import { beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import axios from 'axios'
import {
  getAccessToken, invalidateAuthentication, loginWithBrowserSession,
  logoutBrowserSession, refreshAccessToken, restoreAuthentication, subscribeAuthentication,
} from '../src/auth/browserSession.js'

function storage() {
  const items = new Map()
  return {
    getItem: (key) => items.get(key) ?? null,
    setItem: (key, value) => items.set(key, value),
    removeItem: (key) => items.delete(key),
  }
}

function rejectRequest(status, config) {
  throw new axios.AxiosError('test response', undefined, config, null, { status })
}

beforeEach(() => {
  globalThis.sessionStorage = storage()
  globalThis.localStorage = storage()
  globalThis.window = new EventTarget()
  let queue = Promise.resolve()
  window.navigator = {
    locks: {
      request: (_name, action) => {
        const request = queue.then(action)
        queue = request.catch(() => {})
        return request
      },
    },
  }
  window.crypto = globalThis.crypto
  window.atob = globalThis.atob
  invalidateAuthentication()
})

test('새 탭은 탭에 토큰이 없어도 쿠키로 인증을 복구한다', async () => {
  axios.defaults.adapter = async (config) => {
    assert.equal(config.url, '/api/auth/refresh')
    assert.equal(config.withCredentials, true)
    return { data: { accessToken: 'restored-token' }, status: 200, config }
  }
  assert.equal(await restoreAuthentication(), true)
  assert.equal(getAccessToken(), 'restored-token')
})

test('탭에 남은 다른 계정 토큰도 앱 시작 시 쿠키의 계정으로 교체한다', async () => {
  sessionStorage.setItem('accessToken', 'old-account')
  axios.defaults.adapter = async (config) =>
    ({ data: { accessToken: 'cookie-account' }, status: 200, config })
  await restoreAuthentication()
  assert.equal(getAccessToken(), 'cookie-account')
})

test('동시에 필요한 토큰 재발급은 한 요청으로 합친다', async () => {
  let calls = 0
  axios.defaults.adapter = async (config) => {
    calls += 1
    return { data: { accessToken: 'shared-token' }, status: 200, config }
  }
  const tokens = await Promise.all([refreshAccessToken(), refreshAccessToken()])
  assert.deepEqual(tokens, ['shared-token', 'shared-token'])
  assert.equal(calls, 1)
})

test('이미 로그인된 브라우저에서는 다른 계정의 로그인 요청을 보내지 않는다', async () => {
  const paths = []
  axios.defaults.adapter = async (config) => {
    paths.push(config.url)
    return { data: { accessToken: 'existing-account' }, status: 200, config }
  }
  await loginWithBrowserSession('other-account', 'dummy')
  assert.deepEqual(paths, ['/api/auth/refresh'])
  assert.equal(getAccessToken(), 'existing-account')
})

test('동시에 로그인해도 뒤 요청은 먼저 로그인한 계정을 사용한다', async () => {
  let cookieValid = false
  let loginCount = 0
  axios.defaults.adapter = async (config) => {
    if (config.url.endsWith('/refresh') && !cookieValid) rejectRequest(401, config)
    if (config.url.endsWith('/login')) {
      loginCount += 1
      cookieValid = true
    }
    return { data: { accessToken: 'first-account' }, status: 200, config }
  }
  await Promise.all([
    loginWithBrowserSession('first', 'dummy'),
    loginWithBrowserSession('second', 'dummy'),
  ])
  assert.equal(loginCount, 1)
})

test('서버 장애를 인증 만료로 취급하지 않고 로그인도 시도하지 않는다', async () => {
  sessionStorage.setItem('accessToken', 'existing-token')
  const paths = []
  axios.defaults.adapter = async (config) => {
    paths.push(config.url)
    rejectRequest(503, config)
  }
  await assert.rejects(restoreAuthentication())
  await assert.rejects(loginWithBrowserSession('other', 'dummy'))
  assert.equal(getAccessToken(), 'existing-token')
  assert.equal(paths.some((path) => path.endsWith('/login')), false)
})

test('재발급이 거절되면 익명 상태가 되고 남은 탭 토큰도 정리한다', async () => {
  sessionStorage.setItem('accessToken', 'stale')
  axios.defaults.adapter = async (config) => rejectRequest(401, config)
  assert.equal(await restoreAuthentication(), false)
  assert.equal(getAccessToken(), null)
})

test('로그아웃은 서버 쿠키 삭제 후 다른 탭에 토큰 없는 변경 알림을 남긴다', async () => {
  sessionStorage.setItem('accessToken', 'existing-token')
  axios.defaults.adapter = async (config) => ({ status: 204, config })
  let change
  const unsubscribe = subscribeAuthentication((event) => { change = event })
  await logoutBrowserSession()
  assert.equal(getAccessToken(), null)
  assert.equal(change.kind, 'logout')
  const notification = JSON.parse(localStorage.getItem('sideworks.auth-change'))
  assert.deepEqual(Object.keys(notification).sort(), ['id', 'kind'])
  unsubscribe()
})

test('쿠키 삭제 실패 시 로그아웃 완료를 알리거나 인증을 지우지 않는다', async () => {
  sessionStorage.setItem('accessToken', 'existing-token')
  axios.defaults.adapter = async (config) => rejectRequest(503, config)
  await assert.rejects(logoutBrowserSession())
  assert.equal(getAccessToken(), 'existing-token')
  assert.equal(localStorage.getItem('sideworks.auth-change'), null)
})

test('다른 탭 변경 후 늦게 도착한 재발급 응답이 인증을 되살리지 않는다', async () => {
  let respond
  const started = new Promise((resolve) => {
    axios.defaults.adapter = (config) => new Promise((done) => {
      respond = () => done({ data: { accessToken: 'late-token' }, status: 200, config })
      resolve()
    })
  })
  const refresh = refreshAccessToken()
  await started
  invalidateAuthentication()
  respond()
  await assert.rejects(refresh)
  assert.equal(getAccessToken(), null)
})
