import { api } from '../utils/request'
import type { Appointment, PageResult } from '../types'
export const appointmentApi = {
  list: (role: string, page: number) =>
    api<PageResult<Appointment>>(
      `${role === 'ADMIN' ? '/admin/appointments' : role === 'PATIENT' ? '/patient/appointments' : '/staff/appointments'}?page=${page}`,
    ),
  book: (input: { slotId: number; requestKey: string; patientId?: number }) =>
    api('/patient/appointments', 'POST', input),
  transition: (role: string, id: number, action: string) =>
    api(
      `/${role === 'ADMIN' ? 'admin' : role === 'PATIENT' ? 'patient' : 'staff'}/appointments/${id}/${action}`,
      'POST',
    ),
}
