import { api } from '../utils/request'
export interface Tenant {
  id: number
  code: string
  name: string
  enabled: boolean
}
export const platformApi = {
  list: () => api<Tenant[]>('/platform/tenants'),
  create: (input: unknown) => api('/platform/tenants', 'POST', input),
  update: (id: number, input: unknown) => api(`/platform/tenants/${id}`, 'PUT', input),
}
