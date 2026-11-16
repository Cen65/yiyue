import { expect, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
const envPath = path.resolve(process.cwd(), '../.env')
const settings = fs.existsSync(envPath)
  ? Object.fromEntries(
      fs
        .readFileSync(envPath, 'utf8')
        .split(/\r?\n/)
        .filter((l) => l.includes('='))
        .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
    )
  : {}
export async function login(page: Page, role = 'patient', explicitPassword?: string) {
  const password =
    explicitPassword ||
    (role === 'admin'
      ? process.env.DEMO_ADMIN_PASSWORD || settings.DEMO_ADMIN_PASSWORD
      : process.env.DEMO_PATIENT_PASSWORD || settings.DEMO_PATIENT_PASSWORD)
  if (!password) throw new Error('请通过本地 .env 或环境变量配置演示患者密码')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '欢迎使用医约' })).toBeVisible()
  await page.getByLabel('账号', { exact: true }).fill(role)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: role === 'admin' ? '排班与号源' : '预约挂号', exact: true }),
  ).toBeVisible()
}
