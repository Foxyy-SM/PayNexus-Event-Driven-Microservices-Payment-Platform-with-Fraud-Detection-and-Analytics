import { expect, test } from '@playwright/test'

test('member can navigate core payment experience', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/dashboard/)
  await expect(page.getByText('Good morning, Avery.')).toBeVisible()
  await page.getByRole('link', { name: 'Make a payment' }).click()
  await page.getByLabel('Payment amount').fill('25.00')
  await page.getByPlaceholder('Who are you paying?').fill('Lumen Market')
  await page.getByRole('button', { name: /review & authorize/i }).click()
  await expect(page.getByText('Payment initiated')).toBeVisible()
})

test('admin can approve a deterministic review and inspect operations', async ({ page }) => {
  await page.goto('/admin/fraud')
  await expect(page.getByText('Review queue')).toBeVisible()
  await expect(page.getByText('Paper & Pine')).toBeVisible()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByRole('status')).toContainText('Payment approved')
  await page.goto('/status')
  await expect(page.getByText('All core systems operational')).toBeVisible()
})
