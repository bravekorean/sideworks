import apiClient from './apiClient'

export async function getAvailableTemplates(page = 0, size = 30) {
  const response = await apiClient.get('/approval-templates', { params: { page, size } })
  return response.data
}

export async function getManagedTemplates(page = 0, size = 30) {
  const response = await apiClient.get('/approval-templates/manage', { params: { page, size } })
  return response.data
}

export async function getTemplateDepartments() {
  const response = await apiClient.get('/approval-templates/manage/departments')
  return response.data
}

export async function getTemplateMembers(scope, departmentId, page = 0, size = 100, reference = false) {
  const response = await apiClient.get('/approval-templates/manage/members', {
    params: { scope, departmentId: scope === 'DEPARTMENT' ? departmentId : undefined, page, size, reference },
  })
  return response.data
}

export async function resolveTemplate(id) {
  const response = await apiClient.post(`/approval-templates/${id}/resolve`)
  return response.data
}

export async function createTemplate(request) {
  const response = await apiClient.post('/approval-templates', request)
  return response.data
}

export async function updateTemplate(id, request) {
  const response = await apiClient.put(`/approval-templates/${id}`, request)
  return response.data
}

export async function deleteTemplate(id, version) {
  await apiClient.delete(`/approval-templates/${id}`, { params: { version } })
}
