import { test, expect } from '@playwright/test'
import { randomBytes } from 'node:crypto'
import { login } from './helpers'

test('管理员新增并停用无排班院区', async ({ page }) => {
  await login(page, 'admin')
  await page.getByRole('button', { name: '基础运营', exact: true }).click()
  await expect(page.getByRole('heading', { name: '院区管理', exact: true })).toBeVisible()
  const name = `浏览器院区${Date.now()}`
  await page.getByRole('button', { name: '新增', exact: true }).click()
  await page.getByLabel('名称', { exact: true }).fill(name)
  await page.getByLabel('地址', { exact: true }).fill('虚构测试地址')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('保存成功')
  const row = page.locator('.resource-row').filter({ hasText: name })
  await row.getByRole('button', { name: '编辑' }).click()
  await page.getByRole('combobox', { name: '状态', exact: true }).selectOption({ label: '停用' })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(row).toContainText('停用')
})

test('管理员维护医生关联并开通受限医生账号', async ({ page }) => {
  await login(page, 'admin')
  await page.getByRole('button', { name: '基础运营', exact: true }).click()
  await page.getByRole('button', { name: '医生管理', exact: true }).click()
  const name = `浏览器医生${Date.now()}`
  await page.getByRole('button', { name: '新增', exact: true }).click()
  await page.getByLabel('名称', { exact: true }).fill(name)
  await page.getByLabel('职称', { exact: true }).fill('测试医师')
  const overview = await (await page.request.get('/api/admin/operations')).json()
  await page
    .getByRole('listbox', { name: /关联科室/ })
    .selectOption(String(overview.departments.find((d: { enabled: boolean }) => d.enabled).id))
  await page
    .getByRole('listbox', { name: /关联院区/ })
    .selectOption(String(overview.campuses.find((c: { enabled: boolean }) => c.enabled).id))
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('保存成功')
  await page.getByRole('button', { name: '医护账号', exact: true }).click()
  await page.getByRole('button', { name: '新增医护账号' }).click()
  const username = `doc_${Date.now()}`
  const password = randomBytes(18).toString('hex')
  await page.getByLabel('名称', { exact: true }).fill(name)
  await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('初始密码', { exact: true }).fill(password)
  await page.getByRole('combobox', { name: '岗位', exact: true }).selectOption('DOCTOR')
  await page.getByRole('combobox', { name: '绑定医生', exact: true }).selectOption({ label: name })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('保存成功')
  await page.locator('.profile').click()
  await login(page, username, password)
  await expect(page.getByRole('button', { name: '基础运营', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '智能导诊', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '预约管理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '预约管理', exact: true })).toBeVisible()
  await expect(page.locator('.appointment-row')).toHaveCount(0)
})

test('患者建立授权档案并为家属预约取消', async ({ page }) => {
  await login(page)
  await page.getByRole('button', { name: '就诊人管理', exact: true }).click()
  await page.getByRole('button', { name: '新增就诊人' }).click()
  const name = `浏览器家属${Date.now()}`
  await page.getByLabel('就诊人姓名').fill(name)
  await page.getByRole('combobox', { name: '关系', exact: true }).selectOption('CHILD')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '保存档案' }).click()
  await expect(page.locator('.resource-row').filter({ hasText: name })).toBeVisible()
  await page.getByRole('button', { name: '预约挂号', exact: true }).click()
  await page.getByRole('button', { name: '选择时段' }).first().click()
  await page.locator('.slot-grid button:enabled').first().click()
  await page
    .getByRole('combobox', { name: '预约就诊人', exact: true })
    .selectOption({ label: `${name} · 已授权就诊人` })
  await page.getByRole('button', { name: '确认预约', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的预约', exact: true })).toBeVisible()
  const row = page.locator('.appointment-row').filter({ hasText: name })
  await expect(row).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await row.getByRole('button', { name: '取消预约' }).click()
  await expect(row).toContainText('已取消')
})

test('平台管理页面提供机构开户表单', async ({ page }) => {
  // 平台界面用明确的角色/数据测试桩，不向普通管理员授予真实平台权限。
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ json: { id: 1, tenantId: 1, name: '平台界面测试', role: 'PLATFORM_ADMIN' } }),
  )
  await page.route('**/api/platform/tenants', (route) =>
    route.fulfill({ json: [{ id: 1, code: 'platform', name: '平台演示', enabled: true }] }),
  )
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '机构开户与启停', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新建机构' }).click()
  await expect(page.getByLabel('机构编码', { exact: true })).toBeVisible()
  await expect(page.getByLabel('管理员初始密码', { exact: true })).toHaveAttribute(
    'type',
    'password',
  )
  await expect(page.getByRole('button', { name: '智能导诊', exact: true })).toHaveCount(0)
})
