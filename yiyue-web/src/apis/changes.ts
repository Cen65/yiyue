import { api } from '../utils/request'
export interface ChangeJob {
  id: number
  scheduleId: number
  replacementScheduleId: number | null
  kind: string
  reason: string
  status: string
  total: number
  done: number
  failed: number
  campusId: number
  departmentId: number
}
export interface ChangeItem {
  id: number
  changeId: number
  appointmentId: number
  targetScheduleId: number | null
  newAppointmentId: number | null
  status: string
  decision: string
  attempts: number
  lastError: string | null
  reason: string
}
export const changesApi = {
  create: (id: number, body: { reason: string; kind: string; replacementDoctorId?: number }) =>
    api<ChangeJob>(`/admin/schedules/${id}/change`, 'POST', body),
  list: () => api<ChangeJob[]>('/admin/changes'),
  items: (id: number) => api<ChangeItem[]>(`/admin/changes/${id}/items`),
  process: (id: number) => api(`/admin/changes/${id}/process`, 'POST'),
  retry: (id: number) => api(`/admin/changes/${id}/retry`, 'POST'),
  propose: (id: number, scheduleId: number) =>
    api(`/admin/changes/items/${id}/proposal`, 'POST', { scheduleId }),
  mine: () => api<ChangeItem[]>('/patient/changes'),
  accept: (id: number, slotId: number) =>
    api(`/patient/changes/${id}/accept`, 'POST', { slotId, consent: true }),
  decline: (id: number) => api(`/patient/changes/${id}/decline`, 'POST'),
}
