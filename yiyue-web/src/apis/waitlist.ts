import { api } from '../utils/request'
export interface WaitEntry {
  startAt: string
  endAt: string
  doctorName: string
  campusName: string
  patientName: string
  id: number
  slotId: number
  patientId: number
  status: string
  expiresAt: string | null
  appointmentId: number | null
}
export interface Inbox {
  id: number
  message: string
  readAt: string | null
}
export const waitlistApi = {
  join: (slotId: number, patientId?: number) =>
    api<WaitEntry>('/patient/waitlist', 'POST', { slotId, patientId }),
  list: () => api<WaitEntry[]>('/patient/waitlist'),
  accept: (id: number) => api(`/patient/waitlist/${id}/accept`, 'POST', { consent: true }),
  cancel: (id: number) => api(`/patient/waitlist/${id}/cancel`, 'POST'),
  inbox: () => api<Inbox[]>('/patient/inbox'),
  read: (id: number) => api(`/patient/inbox/${id}/read`, 'POST'),
}
