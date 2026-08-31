import apiClient from './apiClient'

export async function getMyProfile() {
  const response = await apiClient.get('/users/mypage')

  return response.data
}

export async function getDirectory(page = 0, size = 20) {
  const response = await apiClient.get('/users/directory', {
    params : { page, 
               size,},
  })

  return response.data
}

export async function updateMyProfile(userEmail, userPhone) {
    await apiClient.patch('/users/mypage', {
        userEmail,
        userPhone,
    })
}

export async function changeMyPassword(currentPassword, newPassword) {
  await apiClient.patch('/users/mypage/password', {
    currentPassword,
    newPassword,
  })
}

export async function withdrawMyAccount(password) {
  await apiClient.delete('/users/mypage', {
    data: { password },
  })
}
