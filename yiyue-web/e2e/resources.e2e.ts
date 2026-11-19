import { test, expect } from '@playwright/test'
import { findScheduleRow, publishSchedule, post } from './scheduleFixture'
import { login } from './helpers'
test('管理员建立诊室并发布联合排班，患者可查看出诊资源', async ({ page }) => {
  const { schedule, replacement } = await publishSchedule(page)
  await page.getByRole('button', { name: '诊室与设备', exact: true }).click()
  await page.getByRole('button', { name: '新增资源', exact: true }).click()
  const name = `虚构诊室${Date.now()}`
  await page.getByLabel('资源名称').fill(name)
  await page.getByLabel('所属院区').selectOption(String(schedule.campusId))
  await page.getByRole('button', { name: '保存资源', exact: true }).click()
  await expect(page.locator('.resource-item').filter({ hasText: name })).toBeVisible()
  await page.getByRole('button', { name: '排班与号源', exact: true }).click()
  await page.getByRole('button', { name: '发布排班', exact: true }).click()
  await page
    .getByRole('combobox', { name: '院区', exact: true })
    .selectOption(String(schedule.campusId))
  await page
    .getByRole('combobox', { name: '医生', exact: true })
    .selectOption(String(replacement.id))
  await page
    .getByRole('combobox', { name: '出诊科室', exact: true })
    .selectOption(String(schedule.departmentId))
  await page.getByLabel('开始时间').fill(schedule.startAt.slice(0, 16))
  await page.getByLabel('结束时间').fill(schedule.endAt.slice(0, 16))
  const resources = await (await page.request.get('/api/resources')).json()
  await page
    .getByRole('listbox', { name: '联合占用资源（可多选）' })
    .selectOption(String(resources.find((r: { name: string }) => r.name === name).id))
  await page.getByRole('button', { name: '发布排班与号源', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('排班已发布')
  await page.locator('.profile').click()
  await login(page)
  const row = await findScheduleRow(page, replacement.name)
  await row.getByRole('button', { name: '选择时段' }).click()
  await expect(page.locator('.modal')).toContainText(name)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: test.info().outputPath('resource-mobile.png'), fullPage: true })
})
