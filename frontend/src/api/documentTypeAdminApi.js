import apiClient from './apiClient'

const endpoint = '/admin/approval-document-types'

export async function getAdminDocumentTypes() {
  return (await apiClient.get(endpoint)).data
}

export async function createDocumentType(request) {
  return (await apiClient.post(endpoint, request)).data
}

export async function updateDocumentType(id, request) {
  return (await apiClient.put(`${endpoint}/${id}`, request)).data
}
