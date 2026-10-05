import apiClient from './apiClient'

export async function getDelegations(delegatorId) {
  const response = await apiClient.get('/approval-delegations', {
    params: delegatorId ? { delegatorId } : {},
  })
  return response.data
}

export async function createDelegation(input) {
  const response = await apiClient.post('/approval-delegations', input)
  return response.data
}

export async function cancelDelegation(id) {
  await apiClient.delete(`/approval-delegations/${id}`)
}
