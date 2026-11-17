import { test, expect } from '@playwright/test'
import { post, prepare } from './scheduleFixture'
import { login } from './helpers'
test('家属候补获得保留后确认并读取通知', async ({ page }) => {
  const { name, appointment } = await prepare(page)
  await page.locator('.profile').click()
  await login(page)
  const patient = await post(page, '/patient/profiles', {
    name: '虚构候补家属',
    relationship: 'OTHER',
    consent: true,
  })
  // 重新加载档案，使候补选择使用真实授权列表。
  await page.reload()
  const row = page.locator('.doctor-card').filter({ hasText: name }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '选择时段' }).click()
  await page.getByRole('combobox', { name: '候补就诊人' }).selectOption(String(patient.id))
  await page.getByRole('button', { name: '09:00—09:30 加入候补' }).click()
  await expect(page.getByRole('heading', { name: '候补与消息', exact: true })).toBeVisible()
  const entries = await (await page.request.get('/api/patient/waitlist')).json()
  const entry = entries.find((e: { patientId: number }) => e.patientId === patient.id)
  const waiting = page.locator('.wait-row').filter({ hasText: `候补 #${entry.id} ·` })
  await expect(waiting).toContainText('排队中')
  await post(page, `/patient/appointments/${appointment.id}/cancel`, undefined)
  await expect(waiting).toContainText('待确认', { timeout: 15000 })
  await expect(waiting).toContainText(name)
  page.once('dialog', (d) => d.accept())
  await waiting.getByRole('button', { name: '确认候补', exact: true }).click()
  await expect(waiting).toContainText('已确认')
  await expect(waiting).toContainText('新预约 #')
  await expect(page.getByRole('button', { name: '标为已读' }).first()).toBeVisible({
    timeout: 15000,
  })
  await page.getByRole('button', { name: '标为已读' }).first().click()
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: test.info().outputPath('waitlist-mobile.png'), fullPage: true })
})
