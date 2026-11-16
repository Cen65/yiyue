import { api } from '../utils/request'
export interface PatientProfile {
  id: number
  name: string
  relationship: string
  authorized: boolean
  selfProfile: boolean
}
export const profilesApi = {
  list: () => api<PatientProfile[]>('/patient/profiles'),
  save: (id: number | null, input: unknown) =>
    api(`/patient/profiles${id === null ? '' : `/${id}`}`, id === null ? 'POST' : 'PUT', input),
  revoke: (id: number) => api(`/patient/profiles/${id}/revoke`, 'POST', {}),
}
