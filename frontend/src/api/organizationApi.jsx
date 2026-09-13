import apiClient from './apiClient'

export async function getOrganizationDepartments() {
  const response = await apiClient.get('/organization/departments')

  return response.data
}

export async function getDepartmentMembers(departmentId, page = 0, size = 20) {
  const response = await apiClient.get(
    `/organization/departments/${departmentId}/members`,
    { params: { page, size } },
  )

  return response.data
}
