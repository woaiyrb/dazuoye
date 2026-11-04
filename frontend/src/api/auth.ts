import { request } from './client'
import type { CurrentUser, LoginRequest, LoginResult } from '@/types/models'

export const authApi = {
  login(payload: LoginRequest) {
    return request<LoginResult>('POST', '/auth/session', { data: payload })
  },
  logout() {
    return request<void>('DELETE', '/auth/session')
  },
  me() {
    return request<CurrentUser>('GET', '/me')
  },
}
