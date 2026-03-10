import { test, expect } from '@playwright/test';

test.describe('Smoke tests', () => {
  test('login page loads', async ({ page }) => {
    // Clear auth cookies so we see the login page (not redirect to dashboard)
    await page.context().clearCookies();
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await expect(page.getByRole('heading', { name: /sign in|login|welcome/i })).toBeVisible();
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test('dashboard shows KPI cards after login', async ({ page }) => {
    // With storageState the user is already authenticated; go directly to dashboard
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });

    // Expect to be on dashboard
    await expect(page).toHaveURL('/');

    // At least one KPI card should be visible
    await expect(page.locator('.kpi-card, [data-testid="kpi-card"]').first()).toBeVisible({ timeout: 10_000 });
  });

  test('tickets list page loads', async ({ page }) => {
    await page.goto('/tickets');
    await page.waitForLoadState('networkidle');
    await expect(page.getByRole('heading', { name: /support tickets/i })).toBeVisible();
  });

  test('new ticket modal opens', async ({ page }) => {
    await page.goto('/tickets');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /new ticket/i }).click();
    await expect(page.getByRole('heading', { name: /new support ticket/i })).toBeVisible();
  });
});
