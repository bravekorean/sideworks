import apiClient from './apiClient'

export async function getAdminPositions(page = 0, size = 100) {
  const response = await apiClient.get('/admin/positions', {
    params: { page, size, sort: 'positionOrder,asc' },
  })

  return response.data
}

export async function createPosition(positionName, positionOrder) {
  const response = await apiClient.post('/admin/positions', {
    positionName,
    positionOrder,
  })

  return response.data.positionId
}

export async function updatePosition(positionId, positionName, positionOrder) {
  await apiClient.put(`/admin/positions/${positionId}`, {
    positionName,
    positionOrder,
  })
}

export async function deletePosition(positionId) {
  await apiClient.delete(`/admin/positions/${positionId}`)
}

export async function getAdminDepartments(page = 0, size = 100) {
  const response = await apiClient.get('/admin/departments', {
    params: { page, size, sort: 'departmentId,asc' },
  })
  return response.data
}

export async function createDepartment(departmentName, parentDepartmentId) {
  const response = await apiClient.post('/admin/departments', {
    departmentName,
    parentDepartmentId,
  })
  return response.data.departmentId
}

export async function updateDepartment(departmentId, departmentName, parentDepartmentId) {
  await apiClient.put(`/admin/departments/${departmentId}`, {
    departmentName,
    parentDepartmentId,
  })
}

export async function updateDepartmentManager(departmentId, managerUserId) {
  await apiClient.patch(`/admin/departments/${departmentId}/manager`, {
    managerUserId,
  })
}

export async function deleteDepartment(departmentId) {
  await apiClient.delete(`/admin/departments/${departmentId}`)
}

export async function getAdminUsers(page = 0, size = 100) {
  const response = await apiClient.get('/admin/users', {
    params: { page, size, sort: 'userId,asc' },
  })
  return response.data
}

export async function getAdminUser(userId) {
  const response = await apiClient.get(`/admin/users/${userId}`)
  return response.data
}

export async function createAdminUser(user) {
  const response = await apiClient.post('/admin/users', user)
  return response.data
}

export async function updateAdminUser(userId, user) {
  await apiClient.patch(`/admin/users/${userId}`, user)
}

export async function assignAdminUser(userId, departmentId, positionId) {
  await apiClient.patch(`/admin/users/${userId}/assignment`, {
    departmentId,
    positionId,
  })
}

export async function changeAdminUserRole(userId, userRole) {
  await apiClient.patch(`/admin/users/${userId}/role`, { userRole })
}

export async function changeAdminUserStatus(userId, status) {
  await apiClient.patch(`/admin/users/${userId}/status`, { status })
}
