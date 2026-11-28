import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const env = Object.fromEntries(readFileSync(new URL('../.env', import.meta.url), 'utf8')
  .split(/\r?\n/)
  .filter(line => /^[A-Z_]+=/.test(line))
  .map(line => line.split(/=(.*)/s).slice(0, 2)))
const base = `http://localhost:${env.SIDEWORKS_HTTP_PORT || 8088}`
const attachment = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64')
const attachmentName = 'compose-smoke.png'
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())

async function request(path, { user, json, form, method = 'GET' } = {}) {
  const headers = {}
  if (user) headers.Authorization = `Bearer ${user.accessToken}`
  if (json) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${base}${path}`, {
    method, headers, body: json ? JSON.stringify(json) : form,
    signal: AbortSignal.timeout(20_000),
  })
  if (!response.ok) throw new Error(`${method} ${path} returned HTTP ${response.status}`)
  return response
}

async function login(loginId) {
  const response = await request('/api/auth/login', {
    method: 'POST', json: { loginId, password: env.SIDEWORKS_DEMO_PASSWORD },
  })
  assert.match(response.headers.get('set-cookie') ?? '', /HttpOnly/i)
  const account = await response.json()
  assert.equal(account.loginId, loginId)
  return account
}

async function* readEvents(response) {
  assert.match(response.headers.get('content-type') ?? '', /^text\/event-stream/)
  let pending = ''
  for await (const chunk of response.body.pipeThrough(new TextDecoderStream())) {
    pending += chunk.replaceAll('\r', '')
    let separator = pending.indexOf('\n\n')
    while (separator >= 0) {
      const event = pending.slice(0, separator)
      pending = pending.slice(separator + 2)
      yield event
      separator = pending.indexOf('\n\n')
    }
  }
}

async function verifySaved(id, writer, approver, delegate) {
  const url = `/api/approvals/${id}`
  const detail = await (await request(url, { user: writer })).json()
  assert.equal(detail.approvalStatus, 'APPROVED')
  assert.equal(detail.approvalLines[0].approverId, approver.userId)
  assert.equal(detail.approvalLines[0].processorId, delegate.userId)
  assert.ok(detail.histories.some(row => row.actionType === 'APPROVED'
    && row.actorId === delegate.userId && row.onBehalfOfId === approver.userId))

  const file = detail.attachments.find(item => item.originalFileName === attachmentName)
  assert.ok(file, 'Attachment metadata is missing')
  const download = await request(`${url}/attachments/${file.attachmentId}/download`, { user: writer })
  assert.deepEqual(Buffer.from(await download.arrayBuffer()), attachment)

  const notifications = await (await request('/api/notifications?size=100', { user: writer })).json()
  assert.ok(notifications.content.some(item => item.approvalId === id && item.type === 'APPROVAL_COMPLETED'))
  assert.match(await (await request(`/approvals/${id}`)).text(), /<div id="root">/)
}

const [writer, approver, delegate] = await Promise.all(['demo-writer', 'demo-approver', 'demo-delegate'].map(login))
const existingId = process.argv[2]
if (existingId !== undefined) {
  assert.match(existingId, /^[1-9][0-9]*$/)
  await verifySaved(Number(existingId), writer, approver, delegate)
  console.log(`Verified stored approval, audit, notification and attachment bytes for approval ${existingId}.`)
} else {
  const delegations = await (await request('/api/approval-delegations', { user: approver })).json()
  const current = delegations.find(row => !row.canceledAt && row.startDate <= today && row.endDate >= today)
  if (current) {
    assert.equal(current.delegateeId, delegate.userId)
  } else {
    await request('/api/approval-delegations', { user: approver, method: 'POST', json: {
      delegatorId: approver.userId, delegateeId: delegate.userId, startDate: today, endDate: today,
    } })
  }

  const types = await (await request('/api/approval-document-types', { user: writer })).json()
  const general = types.find(type => type.typeCode === 'GENERAL_PROPOSAL')
  assert.ok(general, 'General proposal document type is missing')
  const draft = await request('/api/approvals', { user: writer, method: 'POST', json: {
    title: 'Compose smoke test', content: 'Local deployment verification', documentTypeId: general.approvalDocumentTypeId,
  } })
  const { approvalId } = await draft.json()
  const path = `/api/approvals/${approvalId}`
  const form = new FormData()
  form.append('files', new Blob([attachment], { type: 'image/png' }), attachmentName)
  await request(`${path}/attachments`, { user: writer, method: 'POST', form })
  await request(`${path}/submit`, { user: writer, method: 'POST', json: { approverIds: [approver.userId], ccUserIds: [] } })
  const pending = await (await request(path, { user: delegate })).json()
  assert.equal(pending.canDecide, true)
  assert.equal(pending.approvalLines[0].processorId, delegate.userId)

  const stream = readEvents(await request('/api/notifications/stream', { user: writer }))
  try {
    assert.match((await stream.next()).value ?? '', /^event:connected/m)
    await request(`${path}/approve`, { user: delegate, method: 'POST', json: { comment: 'Compose smoke test' } })
    let completionReceived = false
    for await (const event of stream) {
      if (!/^event:notification$/m.test(event)) continue
      const { approvalId: notifiedId, type } = JSON.parse(event.match(/^data:(.*)$/m)[1])
      if (notifiedId === approvalId && type === 'APPROVAL_COMPLETED') {
        completionReceived = true
        break
      }
    }
    assert.ok(completionReceived, 'Live completion notification did not arrive')
  } finally {
    await stream.return()
  }
  await verifySaved(approvalId, writer, approver, delegate)
  console.log(`Verified login, submission, delegated approval, SSE and attachment for approval ${approvalId}.`)
}
