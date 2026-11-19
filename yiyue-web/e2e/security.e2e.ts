import { test, expect } from '@playwright/test'
import { login } from './helpers'
test('关闭模拟支付能力后隐藏收费操作入口', async ({ page }) => {
  // 页面能力测试桩；生产profile的真实API关闭由HTTPS演练另行验证。
  await page.route('**/api/auth/capabilities', (route) =>
    route.fulfill({ json: { mockPayments: false } }),
  )
  await login(page, 'admin')
  await expect(page.getByRole('button', { name: '支付对账', exact: true })).toHaveCount(0)
  await expect(page.getByText('当前环境仅开放免费预约，尚未接入收费渠道。')).toBeVisible()
  await page.getByRole('button', { name: '发布排班', exact: true }).click()
  await expect(page.getByLabel('模拟挂号费（分，0为免费）')).toHaveCount(0)
})
