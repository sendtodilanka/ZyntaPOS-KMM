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

/**
 * Stub login endpoint to return a 401 for invalid credentials.
 */
async function stubLoginFailure(page: import('@playwright/test').Page) {
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
      if (url.includes('/admin/auth/status')) {
        return Promise.resolve(new Response(JSON.stringify({ needsBootstrap: false }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      if (url.includes('/admin/auth/login') && init?.method?.toUpperCase() === 'POST') {
        return Promise.resolve(new Response(JSON.stringify({ error: 'Invalid email or password' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return _orig(input, init);
    };
  });
}

/**
 * Ensure the authenticated user is available even before MSW service worker
 * activates.  The addInitScript patches window.fetch at page-creation time,
 * which fires before the MSW service worker intercepts requests.  This
 * guarantees /admin/auth/me always returns the mock user so the auth guard
 * in __root.tsx never redirects to /login.
 */
async function ensureAuthenticated(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    const _orig = window.fetch.bind(window);
    const mockUser = {
      id: 'user-1',
      email: 'admin@zyntapos.com',
      name: 'System Admin',
      role: 'ADMIN',
      mfaEnabled: false,
      isActive: true,
      lastLoginAt: null,
      createdAt: new Date('2024-01-01T00:00:00Z').getTime(),
    };
    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : (input instanceof URL ? input.href : (input as Request).url);
      if (url.includes('/admin/auth/me')) {
        return Promise.resolve(new Response(JSON.stringify(mockUser), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      if (url.includes('/admin/auth/status')) {
        return Promise.resolve(new Response(JSON.stringify({ needsBootstrap: false }), {
          status: 200,
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

  test('login form elements are visible', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Email input
    await expect(page.locator('input#email[type="email"]')).toBeVisible();
    // Password input
    await expect(page.locator('input#password')).toBeVisible();
    // Submit button
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test.fixme('login with invalid credentials shows error message', async ({ page }) => {
    await stubLoginFailure(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Fill in invalid credentials
    await page.locator('input#email').fill('wrong@example.com');
    await page.locator('input#password').fill('wrongpassword');

    // Submit the form
    await page.getByRole('button', { name: /sign in/i }).click();

    // Verify error message appears (login page shows "Invalid email or password." on 401)
    await expect(page.getByText(/invalid email or password/i)).toBeVisible({ timeout: 10_000 });
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('dashboard shows KPI cards after login', async ({ page }) => {
    await ensureAuthenticated(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });

    // Expect to be on dashboard
    await expect(page).toHaveURL('/');

    // At least one KPI card should be visible
    await expect(page.locator('.kpi-card, [data-testid="kpi-card"]').first()).toBeVisible({ timeout: 10_000 });
  });

  test('tickets list page loads', async ({ page }) => {
    await ensureAuthenticated(page);
    await page.goto('/tickets');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page.getByRole('heading', { name: /support tickets/i })).toBeVisible({ timeout: 15_000 });
  });

  test('new ticket modal opens', async ({ page }) => {
    await ensureAuthenticated(page);
    await page.goto('/tickets');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });
    await page.getByRole('button', { name: /new ticket/i }).waitFor({ state: 'visible', timeout: 15_000 });
    await page.getByRole('button', { name: /new ticket/i }).click();
    await expect(page.getByRole('heading', { name: /new support ticket/i })).toBeVisible({ timeout: 10_000 });
  });
});

// ── Main route headings ──────────────────────────────────────────────────────

const mainRoutes = [
  { path: '/licenses',        heading: /licenses/i },
  { path: '/stores',          heading: /stores/i },
  { path: '/users',           heading: /users/i },
  { path: '/tickets',         heading: /support tickets/i },
  { path: '/audit',           heading: /audit/i },
  { path: '/alerts',          heading: /alerts/i },
  { path: '/health',          heading: /store health/i },
  { path: '/sync',            heading: /sync/i },
  { path: '/reports',         heading: /reports/i },
  { path: '/config',          heading: /config/i },
  { path: '/settings/profile', heading: /profile/i },
  { path: '/settings/mfa',   heading: /mfa|two.factor|authenticat/i },
];

test.describe('Route headings', () => {
  for (const { path, heading } of mainRoutes) {
    test(`${path} loads with correct heading`, async ({ page }) => {
      await ensureAuthenticated(page);
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });
      await expect(page.getByRole('heading', { name: heading })).toBeVisible({ timeout: 15_000 });
    });
  }
});
