import { test, expect } from '@playwright/test';

/**
 * Visual regression tests — screenshot snapshots.
 *
 * First run: generates baseline screenshots under e2e/__screenshots__/
 * Subsequent runs: diffs against baseline; fails if pixels change beyond threshold.
 *
 * To update baselines intentionally:
 *   npm run test:update-snapshots
 */

const THRESHOLD = { maxDiffPixelRatio: 0.02 }; // allow 2% pixel diff

// ── Authenticated routes ───────────────────────────────────────────────────────

const routes = [
  { name: 'dashboard',       path: '/'                 },
  { name: 'licenses',        path: '/licenses'         },
  { name: 'stores',          path: '/stores'           },
  { name: 'users',           path: '/users'            },
  { name: 'tickets',         path: '/tickets'          },
  { name: 'audit',           path: '/audit'            },
  { name: 'alerts',          path: '/alerts'           },
  { name: 'health',          path: '/health'           },
  { name: 'sync',            path: '/sync'             },
  { name: 'reports',         path: '/reports'          },
  { name: 'config',          path: '/config'           },
  { name: 'settings',        path: '/settings/profile' },
];

// ── Dark mode (default) ───────────────────────────────────────────────────────

test.describe('Visual — dark mode', () => {
  for (const { name, path } of routes) {
    test(name, async ({ page }) => {
      await page.goto(path);
      await setTheme(page, 'dark');
      await waitForPageStable(page);

      await expect(page).toHaveScreenshot(`dark-${name}.png`, THRESHOLD);
    });
  }
});

// ── Light mode ────────────────────────────────────────────────────────────────

test.describe('Visual — light mode', () => {
  for (const { name, path } of routes) {
    test(name, async ({ page }) => {
      await page.goto(path);
      await setTheme(page, 'light');
      await waitForPageStable(page);

      await expect(page).toHaveScreenshot(`light-${name}.png`, THRESHOLD);
    });
  }
});

// ── Component-level snapshots ─────────────────────────────────────────────────

test.describe('Visual — components', () => {
  test('sidebar — dark mode', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'dark');
    await waitForPageStable(page);
    const sidebar = page.locator('[data-testid="sidebar"], aside, nav').first();
    await expect(sidebar).toHaveScreenshot('sidebar-dark.png', THRESHOLD);
  });

  test('sidebar — light mode', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'light');
    await waitForPageStable(page);
    const sidebar = page.locator('[data-testid="sidebar"], aside, nav').first();
    await expect(sidebar).toHaveScreenshot('sidebar-light.png', THRESHOLD);
  });

  test('topbar — dark mode', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'dark');
    await waitForPageStable(page);
    const topbar = page.locator('header').first();
    await expect(topbar).toHaveScreenshot('topbar-dark.png', THRESHOLD);
  });

  test('topbar — light mode', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'light');
    await waitForPageStable(page);
    const topbar = page.locator('header').first();
    await expect(topbar).toHaveScreenshot('topbar-light.png', THRESHOLD);
  });
});

// ── Responsive snapshots (mobile) ─────────────────────────────────────────────

test.describe('Visual — mobile viewport', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('dashboard — mobile dark', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'dark');
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-dark-dashboard.png', THRESHOLD);
  });

  test('dashboard — mobile light', async ({ page }) => {
    await page.goto('/');
    await setTheme(page, 'light');
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-light-dashboard.png', THRESHOLD);
  });

  test('tickets — mobile dark', async ({ page }) => {
    await page.goto('/tickets');
    await setTheme(page, 'dark');
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-dark-tickets.png', THRESHOLD);
  });
});

// ── Login page (no auth required) ─────────────────────────────────────────────

test.describe('Visual — login page', () => {
  test('dark mode', async ({ page }) => {
    // Clear auth so the login page stays visible
    await page.context().clearCookies();
    await page.goto('/login');
    await setTheme(page, 'dark');
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('dark-login.png', THRESHOLD);
  });

  test('light mode', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/login');
    await setTheme(page, 'light');
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('light-login.png', THRESHOLD);
  });
});

// ── Helpers ───────────────────────────────────────────────────────────────────

async function setTheme(page: import('@playwright/test').Page, theme: 'dark' | 'light') {
  // Sync the Zustand-persisted theme and apply the Tailwind class immediately
  await page.evaluate((t) => {
    const stored = JSON.parse(localStorage.getItem('zynta-admin-ui') ?? '{}');
    localStorage.setItem('zynta-admin-ui', JSON.stringify({ ...stored, theme: t }));
    if (t === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, theme);
}

async function waitForPageStable(page: import('@playwright/test').Page) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(200);
}
