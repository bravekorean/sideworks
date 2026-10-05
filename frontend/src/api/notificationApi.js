import apiClient, { getValidAccessToken } from './apiClient'

export async function getNotifications() {
  const response = await apiClient.get('/notifications', { params: { page: 0, size: 20 } })
  return response.data.content ?? []
}

export async function getUnreadNotificationCount() {
  const response = await apiClient.get('/notifications/unread-count')
  return response.data.count ?? 0
}

export async function markNotificationRead(notificationId) {
  await apiClient.patch(`/notifications/${notificationId}/read`)
}

function handleSseBlock(block, onNotification, onConnected) {
  let eventName = 'message'
  const data = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (eventName === 'connected') onConnected()
  if (eventName === 'notification' && data.length) onNotification(JSON.parse(data.join('\n')))
}

export async function subscribeNotifications({ signal, onNotification, onConnected }) {
  while (!signal.aborted) {
    try {
      let token = await getValidAccessToken()
      let response = await fetch('/api/notifications/stream', {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal,
      })
      if (response.status === 401) {
        token = await getValidAccessToken(true)
        response = await fetch('/api/notifications/stream', {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal,
        })
      }
      if (!response.ok || !response.body) throw new Error(`SSE 연결 실패: ${response.status}`)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!signal.aborted) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\r?\n\r?\n/)
        buffer = blocks.pop() ?? ''
        blocks.forEach((block) => handleSseBlock(block, onNotification, onConnected))
      }
    } catch {
      if (signal.aborted) break
    }
    if (signal.aborted) break
    await new Promise((resolve) => {
      const onAbort = () => { window.clearTimeout(timer); resolve() }
      const timer = window.setTimeout(() => {
        signal.removeEventListener('abort', onAbort)
        resolve()
      }, 3000)
      signal.addEventListener('abort', onAbort, { once: true })
    })
  }
}
