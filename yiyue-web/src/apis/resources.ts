import { api } from '../utils/request'
export interface Resource {
  id: number
  campusId: number
  kind: string
  name: string
  enabled: boolean
}
export const resourcesApi = {
  list: () => api<Resource[]>('/resources'),
  save: (id: number | null, body: Omit<Resource, 'id'>) =>
    api<Resource>(
      `/admin/resources${id === null ? '' : '/' + id}`,
      id === null ? 'POST' : 'PUT',
      body,
    ),
  assigned: (id: number) => api<Resource[]>(`/schedules/${id}/resources`),
}
