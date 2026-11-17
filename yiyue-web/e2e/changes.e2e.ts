import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers'
import { post, prepare } from './scheduleFixture'
test('替诊经患者明确同意后生成新预约', async ({ page }) => {
  const { name, schedule, replacement, appointment } = await prepare(page)
  // 管理员通过真实排班页面发起变更。
  const filter = page.getByRole('combobox', { name: '就诊科室', exact: true })
  await filter.selectOption(String(schedule.departmentId))
  let row = page.locator('.doctor-card').filter({ hasText: name }).first()
  for (let i = 0; i < 20 && !(await row.count()); i++) {
    const next = page.getByRole('button', { name: '下一页', exact: true })
    if (await next.isDisabled()) break
    await next.click()
    await page.waitForTimeout(150)
  }
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '排班变更', exact: true }).click()
  await page.getByLabel('变更方式').selectOption('SUBSTITUTE')
  await page.getByLabel('替诊医生').selectOption(String(replacement.id))
  await page.getByLabel('变更原因').fill('虚构测试：医生临时调整')
  const changeResponse = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/admin/schedules/${schedule.id}/change`) &&
      r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '确认关闭原排班', exact: true }).click()
  expect((await changeResponse).ok()).toBeTruthy()
  await expect(page.locator('.overlay')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '排班变更', exact: true })).toBeVisible()
  const jobs = await (await page.request.get('/api/admin/changes')).json()
  const job = jobs.find((j: { scheduleId: number }) => j.scheduleId === schedule.id)
  await post(page, `/admin/changes/${job.id}/process`, undefined)
  await page.locator('.profile').click()
  await login(page)
  await page.getByRole('button', { name: '变更通知', exact: true }).click()
  const notice = page.locator('.change-record').filter({ hasText: `原预约 #${appointment.id} ·` })
  await expect(notice).toContainText(replacement.name)
  await notice.getByRole('button', { name: '查看建议并选择时段' }).click()
  await expect(page.getByRole('button', { name: '确认改约', exact: true })).toBeDisabled()
  const select = page.getByRole('combobox', { name: '新就诊时段' })
  const options = await select.locator('option:not([disabled])').all()
  await select.selectOption((await options[1].getAttribute('value')) || '')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '确认改约', exact: true }).click()
  await expect(notice).toContainText('已确认改约')
  await expect(notice).toContainText('新预约 #')
  await page.screenshot({ path: test.info().outputPath('changes-desktop.png'), fullPage: true })
})
test('停诊影响清单与移动端通知可用', async ({ page }) => {
  const { schedule, appointment } = await prepare(page)
  const job = await post(page, `/admin/schedules/${schedule.id}/change`, {
    kind: 'STOP',
    reason: '虚构停诊测试',
  })
  await page.getByRole('navigation').getByRole('button', { name: '排班变更', exact: true }).click()
  const jobRow = page.locator('.change-jobs .change-record').filter({ hasText: `#${job.id} ·` })
  await jobRow.getByRole('button', { name: '影响清单' }).click()
  await expect(
    page.locator('.change-record').filter({ hasText: `原预约 #${appointment.id} ·` }),
  ).toBeVisible()
  await post(page, `/admin/changes/${job.id}/process`, undefined)
  await page.locator('.profile').click()
  await login(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '变更通知', exact: true }).click()
  const row = page.locator('.change-record').filter({ hasText: `原预约 #${appointment.id} ·` })
  await expect(row).toContainText('已处理')
  page.once('dialog', (dialog) => dialog.accept())
  await row.getByRole('button', { name: '不接受改约建议' }).click()
  await expect(row).toContainText('已拒绝建议')
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy()
  await page.screenshot({ path: test.info().outputPath('changes-mobile.png'), fullPage: true })
})
