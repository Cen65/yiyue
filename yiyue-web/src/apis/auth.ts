import { api, refreshCsrf } from '../utils/request'
import type { User } from '../types'
export { refreshCsrf }
export const authApi = {
  login: (input: { tenantCode: string; username: string; password: string }) =>
    api<User>('/auth/login', 'POST', input),
  me: () => api<User>('/auth/me'),
  logout: () => api('/auth/logout', 'POST'),
}
