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
 * Ensure the authenticated user is available even before MSW service worker
 * activates.  The addInitScript patches window.fetch at page-creation time,
 * which fires before the MSW service worker intercepts requests.
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

// ── Sidebar nav links ─────────────────────────────────────────────────────────

const navLinks = [
  { label: 'Dashboard',   href: '/'                },
  { label: 'Licenses',    href: '/licenses'        },
  { label: 'Stores',      href: '/stores'          },
  { label: 'Users',       href: '/users'           },
  { label: 'Tickets',     href: '/tickets'         },
  { label: 'Audit',       href: '/audit'           },
  { label: 'Alerts',      href: '/alerts'          },
  { label: 'Health',      href: '/health'          },
  { label: 'Sync',        href: '/sync'            },
  { label: 'Reports',     href: '/reports'         },
  { label: 'Config',      href: '/config'          },
];

test.describe('Sidebar navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  for (const link of navLinks) {
    test(`"${link.label}" → ${link.href}`, async ({ page, viewport }) => {
      test.skip((viewport?.width ?? 1280) < 768, 'sidebar hidden on mobile — use mobile menu test');
      const navLink = page.locator('nav a, aside a').filter({ hasText: new RegExp(link.label, 'i') }).first();
      await navLink.click();
      await expect(page).toHaveURL(link.href);
    });
  }
});

// ── Auth redirect ─────────────────────────────────────────────────────────────

test.describe('Auth guard', () => {
  test('unauthenticated user is redirected to /login', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('login page loads correct heading', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /sign in|login|welcome/i })).toBeVisible();
  });
});

// ── Mobile sidebar ────────────────────────────────────────────────────────────

test.describe('Mobile sidebar', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('opens and closes', async ({ page }) => {
    await page.goto('/');
    const openBtn = page.locator('[aria-label*="menu"], [aria-label*="open sidebar"], button[aria-controls]').first();

    await openBtn.click();

    // Sidebar or drawer should be visible
    const sidebar = page.locator('[data-testid="mobile-sidebar"], [aria-label*="sidebar"], aside[role="dialog"]').first();
    await expect(sidebar).toBeVisible();

    // Close — either a close button or pressing Escape
    await page.keyboard.press('Escape');
    await expect(sidebar).toBeHidden();
  });
});

// ── Theme toggle ──────────────────────────────────────────────────────────────

test.describe('Theme toggle', () => {
  test('switches between dark and light mode', async ({ page }) => {
    await page.goto('/');
    const html = page.locator('html');
    const toggle = page.locator('[aria-label*="theme"], [aria-label*="dark"], [aria-label*="light"]').first();

    // Default: dark mode
    await expect(html).toHaveClass(/dark/);

    // Switch to light
    await toggle.click();
    await expect(html).not.toHaveClass(/dark/);

    // Switch back to dark
    await toggle.click();
    await expect(html).toHaveClass(/dark/);
  });

  test('persists theme choice in localStorage', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('[aria-label*="theme"], [aria-label*="dark"], [aria-label*="light"]').first();
    await toggle.click();

    const stored = await page.evaluate(() => {
      const raw = localStorage.getItem('zynta-admin-ui');
      return raw ? JSON.parse(raw).theme : null;
    });
    expect(stored).toBe('light');
  });
});

// ── Settings navigation ───────────────────────────────────────────────────────

test.describe('Settings navigation', () => {
  test('profile and MFA pages load', async ({ page }) => {
    await ensureAuthenticated(page);
    await page.goto('/settings/profile');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page.getByRole('heading', { name: /profile/i }).first()).toBeVisible({ timeout: 15_000 });

    await page.goto('/settings/mfa');
    await page.waitForLoadState('networkidle');
    await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page.getByRole('heading', { name: /mfa|two.factor|authenticat/i }).first()).toBeVisible({ timeout: 15_000 });
  });
});

// ── Tickets ───────────────────────────────────────────────────────────────────

test.describe('Tickets', () => {
  test('tickets list loads', async ({ page }) => {
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
