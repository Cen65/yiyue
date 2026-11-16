import { api } from '../utils/request'
import type { Catalog, Schedule, Slot, ScheduleCreateReq, PageResult } from '../types'
export const catalogApi = {
  page: (query: Record<string, string>) =>
    api<PageResult<Schedule>>(`/schedules/page?${new URLSearchParams(query)}`),
  catalog: () => api<Catalog>('/catalog'),
  schedules: () => api<Schedule[]>('/schedules'),
  slots: (scheduleId: number) => api<Slot[]>(`/slots?scheduleId=${scheduleId}`),
  create: (input: ScheduleCreateReq) => api<Schedule>('/admin/schedules', 'POST', input),
}
