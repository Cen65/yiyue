import { test, expect, type Page } from '@playwright/test'

import { login } from './helpers'
async function enableMock(page: Page) {
  // 只替换模型接口，不发送症状至外部服务；登录及机构科室仍使用本地真实后端。
  await page.route('**/api/patient/triage/status', (route) =>
    route.fulfill({ json: { enabled: true, provider: 'DeepSeek' } }),
  )
}
async function fill(page: Page) {
  await page
    .getByLabel('请描述您的不适')
    .fill('虚构测试描述：成年人，近几天有不适，需要咨询挂哪个科室。')
  await page.getByRole('checkbox').check()
}
test('未配置模型时明确显示状态并阻止提交', async ({ page }) => {
  await page.route('**/api/patient/triage/status', (route) =>
    route.fulfill({ json: { enabled: false, provider: 'DeepSeek' } }),
  )
  await login(page)
  await page.getByRole('button', { name: '智能导诊', exact: true }).click()
  await expect(page.getByText('智能导诊尚未启用', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '获取导诊参考' })).toBeDisabled()
})
test('取得同意后提交并使用推荐科室筛选排班', async ({ page }) => {
  await enableMock(page)
  await login(page)
  const catalog = await (await page.request.get('/api/catalog')).json()
  const department = catalog.departments[0]
  await page.route('**/api/patient/triage', async (route) => {
    expect(route.request().postDataJSON().consent).toBe(true)
    await route.fulfill({
      json: {
        urgency: 'ROUTINE',
        departments: [department],
        questions: ['症状出现多久了？'],
        message: '浏览器测试桩：科室匹配参考',
      },
    })
  })
  await page.getByRole('button', { name: '智能导诊', exact: true }).click()
  await page.getByLabel('请描述您的不适').fill('虚构测试症状描述')
  await expect(page.getByRole('button', { name: '获取导诊参考' })).toBeDisabled()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '获取导诊参考' }).click()
  await expect(page.getByRole('heading', { name: '科室匹配参考', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看该科排班' }).click()
  await expect(page.getByRole('heading', { name: '预约挂号', exact: true })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '就诊科室', exact: true })).toHaveValue(
    String(department.id),
  )
})
test('紧急情况不显示预约入口', async ({ page }) => {
  await enableMock(page)
  await page.route('**/api/patient/triage', (route) =>
    route.fulfill({
      json: {
        urgency: 'EMERGENCY',
        departments: [],
        questions: [],
        message: '浏览器测试桩：请立即寻求急救，不要等待预约。',
      },
    }),
  )
  await login(page)
  await page.getByRole('button', { name: '智能导诊', exact: true }).click()
  await fill(page)
  await page.getByRole('button', { name: '获取导诊参考' }).click()
  await expect(page.getByRole('heading', { name: '请立即寻求急救' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看该科排班' })).toHaveCount(0)
})
test('接口失败时清楚提示并可重试', async ({ page }) => {
  await enableMock(page)
  await page.route('**/api/patient/triage', (route) =>
    route.fulfill({ status: 504, json: { detail: '导诊服务连接超时，请咨询人工导诊' } }),
  )
  await login(page)
  await page.getByRole('button', { name: '智能导诊', exact: true }).click()
  await fill(page)
  await page.getByRole('button', { name: '获取导诊参考' }).click()
  await expect(page.getByRole('alert')).toContainText('连接超时')
  await expect(page.getByRole('button', { name: '获取导诊参考' })).toBeEnabled()
  await expect(page.locator('.triage-result')).toHaveCount(0)
})
test('移动端导诊与排班页面没有横向溢出', async ({ page }) => {
  await login(page)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '智能导诊', exact: true }).click()
  await expect(page.getByLabel('请描述您的不适')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
