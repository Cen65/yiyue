import { expect, type Page } from '@playwright/test'
import { login } from './helpers'
export async function post(page: Page, url: string, data: unknown) {
  // 使用真实会话 CSRF，测试不绕过后端认证。
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const response = await page.request.post('/api' + url, {
    data,
    headers: { [csrf.headerName]: csrf.token },
  })
  expect(response.ok(), `写入 ${url} 返回 ${response.status()}`).toBeTruthy()
  const body = await response.text()
  return body ? JSON.parse(body) : undefined
}
export async function publishSchedule(page: Page, feeCents = 0) {
  await login(page, 'admin')
  const cat = await (await page.request.get('/api/catalog')).json()
  const campusId = cat.campuses[0].id,
    departmentId = cat.departments[0].id
  const name = `变更测试${Date.now()}`
  const doctor = await post(page, '/admin/operations/doctors', {
    name,
    title: '测试医师',
    specialty: '虚构',
    departmentId,
    departmentIds: [departmentId],
    campusIds: [campusId],
    enabled: true,
  })
  const replacement = await post(page, '/admin/operations/doctors', {
    name: name + '替诊',
    title: '测试医师',
    specialty: '虚构',
    departmentId,
    departmentIds: [departmentId],
    campusIds: [campusId],
    enabled: true,
  })
  const day = new Date(Date.now() + 86400000 * 12).toISOString().slice(0, 10)
  const schedule = await post(page, '/admin/schedules', {
    campusId,
    doctorId: doctor.id,
    departmentId,
    startAt: day + 'T09:00:00',
    endAt: day + 'T10:00:00',
    slotMinutes: 30,
    capacity: 1,
    feeCents,
  })
  const slots = await (await page.request.get(`/api/slots?scheduleId=${schedule.id}`)).json()
  return { name, schedule, replacement, slots }
}
export async function prepare(page: Page) {
  const { name, schedule, replacement, slots } = await publishSchedule(page)
  await page.locator('.profile').click()
  await login(page)
  const appointment = await post(page, '/patient/appointments', {
    slotId: slots[0].id,
    requestKey: `change-${Date.now()}`,
  })
  await page.locator('.profile').click()
  await login(page, 'admin')
  return { name, schedule, replacement, appointment }
}
