export interface User {
  id: number
  tenantId: number
  name: string
  role: 'ADMIN' | 'PATIENT' | 'RECEPTION' | 'DOCTOR' | 'PLATFORM_ADMIN'
}
export interface Campus {
  id: number
  name: string
  address: string
}
export interface Department {
  id: number
  name: string
}
export interface Doctor {
  id: number
  departmentId: number
  name: string
  title: string
  specialty: string
}
export interface Catalog {
  campuses: Campus[]
  departments: Department[]
  doctors: Doctor[]
}
export interface Schedule {
  feeCents?: number
  departmentId: number
  id: number
  campusId: number
  doctorId: number
  startAt: string
  endAt: string
}
export interface Slot {
  id: number
  scheduleId: number
  startAt: string
  endAt: string
  capacity: number
  reserved: number
}
export interface Appointment {
  patientId?: number
  patientName?: string
  id: number
  userId: number
  slotId: number
  status: string
  createdAt: string
  startAt: string
  endAt: string
  doctorName: string
  campusName: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}
export interface ScheduleCreateReq {
  feeCents?: number
  campusId: number
  doctorId: number
  startAt: string
  endAt: string
  slotMinutes: number
  capacity: number
  departmentId?: number
}
export interface TriageResult {
  urgency: 'EMERGENCY' | 'PROMPT' | 'UNDETERMINED' | 'ROUTINE'
  departments: { id: number; name: string }[]
  questions: string[]
  message: string
}
