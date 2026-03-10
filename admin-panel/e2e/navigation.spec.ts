import { test, expect } from '@playwright/test';

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
    // Simulate unauthenticated state — page.route() intercepts before MSW
    await page.route('**/admin/auth/me', route =>
      route.fulfill({ status: 401, body: JSON.stringify({ error: 'Unauthorized' }) }),
    );
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test('login page loads correct heading', async ({ page }) => {
    // Simulate unauthenticated state — page.route() intercepts before MSW
    await page.route('**/admin/auth/me', route =>
      route.fulfill({ status: 401, body: JSON.stringify({ error: 'Unauthorized' }) }),
    );
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
    await page.goto('/settings/profile');
    await expect(page.getByRole('heading', { name: /profile/i })).toBeVisible();

    await page.goto('/settings/mfa');
    await expect(page.getByRole('heading', { name: /mfa|two.factor|authenticator/i })).toBeVisible();
  });
});

// ── Tickets ───────────────────────────────────────────────────────────────────

test.describe('Tickets', () => {
  test('tickets list loads', async ({ page }) => {
    await page.goto('/tickets');
    await expect(page.getByRole('heading', { name: /support tickets/i })).toBeVisible();
  });

  test('new ticket modal opens', async ({ page }) => {
    await page.goto('/tickets');
    await page.getByRole('button', { name: /new ticket/i }).click();
    await expect(page.getByRole('heading', { name: /new support ticket/i })).toBeVisible();
  });
});
