import { api } from '../utils/request'
export interface Resource {
  id: number
  name: string
  enabled: boolean
  address?: string
  title?: string
  specialty?: string
  departmentId?: number
  accountId?: number
}
export interface Overview {
  tenant: Resource
  campuses: Resource[]
  departments: Resource[]
  doctors: Resource[]
  doctorDepartments: { doctorId: number; departmentId: number }[]
  doctorCampuses: { doctorId: number; campusId: number }[]
  staff: { id: number; name: string; username: string; role: string }[]
}
export type ResourceKind = 'tenant' | 'campuses' | 'departments' | 'doctors'
export const operationsApi = {
  overview: () => api<Overview>('/admin/operations'),
  save: (kind: ResourceKind, id: number | null, input: unknown) =>
    api(
      `/admin/operations/${kind}${id === null ? '' : `/${id}`}`,
      id === null ? 'POST' : 'PUT',
      input,
    ),
  staff: (input: unknown) => api('/admin/operations/staff', 'POST', input),
}
