import { api } from '../utils/request'
export interface Payment {
  doctorName: string
  campusName: string
  patientName: string
  startAt: string
  endAt: string
  id: number
  slotId: number
  patientId: number
  amountCents: number
  status: string
  expiresAt: string
  appointmentId: number | null
  channel: string
}
export interface Reconciliation {
  id: number
  status: string
  amountCents: number
  channel: string
  matched: boolean
}
export const paymentsApi = {
  create: (body: { slotId: number; patientId?: number; requestKey: string }) =>
    api<Payment>('/patient/payment-orders', 'POST', body),
  list: () => api<Payment[]>('/patient/payment-orders'),
  pay: (id: number) =>
    api<Payment>(`/patient/payment-orders/${id}/mock-pay`, 'POST', { consent: true }),
  cancel: (id: number) => api(`/patient/payment-orders/${id}/cancel`, 'POST'),
  reconcile: () => api<Reconciliation[]>('/admin/payment-reconciliation'),
}
