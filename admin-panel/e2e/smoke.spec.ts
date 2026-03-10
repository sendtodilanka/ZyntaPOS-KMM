import { test, expect } from '@playwright/test';

/** Override window.fetch before MSW registers its service worker handler.
 *  page.route() runs at CDP level but is bypassed when MSW handles the
 *  request internally via service worker.  Patching window.fetch in the
 *  page context (addInitScript) fires before the browser network layer and
 *  therefore before MSW, so our stub wins.
 */
async function stubUnauthenticated(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    const _orig = window.fetch.bind(window);
    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : (input instanceof URL ? input.href : (input as Request).url);
      if (url.includes('/admin/auth/me')) {
        return Promise.resolve(new Response(JSON.stringify({ error: 'Unauthorized' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return _orig(input, init);
    };
  });
}

test.describe('Smoke tests', () => {
  test('login page loads', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await expect(page.getByRole('heading', { name: /sign in|login|welcome/i })).toBeVisible();
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
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
