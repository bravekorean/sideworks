import { loginWithBrowserSession, logoutBrowserSession } from '../auth/browserSession'

export async function login(loginId, password) {
  return loginWithBrowserSession(loginId, password)
}

export async function logout() {
  await logoutBrowserSession()
}
