import { test, expect } from '@playwright/test';

test.describe('Smoke tests', () => {
  test('login page loads', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /sign in|login|welcome/i })).toBeVisible();
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test('dashboard shows KPI cards after login', async ({ page }) => {
    // Navigate to login
    await page.goto('/login');

    // Fill credentials (uses env vars or defaults for test environment)
    await page.getByLabel(/email/i).fill(process.env.E2E_EMAIL ?? 'admin@zyntapos.com');
    await page.getByLabel(/password/i).fill(process.env.E2E_PASSWORD ?? 'password');
    await page.getByRole('button', { name: /sign in|log in/i }).click();

    // Expect redirect to dashboard
    await expect(page).toHaveURL('/');

    // At least one KPI card should be visible
    await expect(page.locator('.kpi-card, [data-testid="kpi-card"]').first()).toBeVisible({ timeout: 10_000 });
  });

  test('tickets list page loads', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(process.env.E2E_EMAIL ?? 'admin@zyntapos.com');
    await page.getByLabel(/password/i).fill(process.env.E2E_PASSWORD ?? 'password');
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL('/');

    await page.goto('/tickets');
    await expect(page.getByRole('heading', { name: /support tickets/i })).toBeVisible();
  });

  test('new ticket modal opens', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(process.env.E2E_EMAIL ?? 'admin@zyntapos.com');
    await page.getByLabel(/password/i).fill(process.env.E2E_PASSWORD ?? 'password');
    await page.getByRole('button', { name: /sign in|log in/i }).click();

    await page.goto('/tickets');
    await page.getByRole('button', { name: /new ticket/i }).click();
    await expect(page.getByRole('heading', { name: /new support ticket/i })).toBeVisible();
  });
});
