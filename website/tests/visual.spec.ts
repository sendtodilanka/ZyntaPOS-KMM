import { test, expect } from '@playwright/test';

/**
 * Visual regression tests — screenshot snapshots.
 *
 * First run: generates baseline screenshots under tests/__screenshots__/
 * Subsequent runs: diffs against baseline; fails if pixels change beyond threshold.
 *
 * To update baselines intentionally:
 *   npx playwright test --update-snapshots
 */

const routes = [
  { name: 'home',       path: '/'           },
  { name: 'features',   path: '/features'   },
  { name: 'pricing',    path: '/pricing'    },
  { name: 'industries', path: '/industries' },
  { name: 'download',   path: '/download'   },
  { name: 'about',      path: '/about'      },
  { name: 'support',    path: '/support'    },
  { name: 'blog',       path: '/blog'       },
];

const THRESHOLD = { maxDiffPixelRatio: 0.02 }; // allow 2% pixel diff

// ── Dark mode (default) ───────────────────────────────────────────────────────

test.describe('Visual — dark mode', () => {
  for (const { name, path } of routes) {
    test(name, async ({ page }) => {
      await page.goto(path);
      await page.evaluate(() => document.documentElement.classList.remove('light'));
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
      await page.evaluate(() => document.documentElement.classList.add('light'));
      await waitForPageStable(page);

      await expect(page).toHaveScreenshot(`light-${name}.png`, THRESHOLD);
    });
  }
});

// ── Component-level snapshots ─────────────────────────────────────────────────

test.describe('Visual — components', () => {
  test('header — dark mode', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.remove('light'));
    const header = page.locator('#site-header');
    await expect(header).toHaveScreenshot('header-dark.png', THRESHOLD);
  });

  test('header — light mode', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.add('light'));
    const header = page.locator('#site-header');
    await expect(header).toHaveScreenshot('header-light.png', THRESHOLD);
  });

  test('hero section — dark mode', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.remove('light'));
    await waitForPageStable(page);
    const hero = page.locator('section').first();
    await expect(hero).toHaveScreenshot('hero-dark.png', THRESHOLD);
  });

  test('hero section — light mode', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.add('light'));
    await waitForPageStable(page);
    const hero = page.locator('section').first();
    await expect(hero).toHaveScreenshot('hero-light.png', THRESHOLD);
  });
});

// ── Responsive snapshots (mobile) ─────────────────────────────────────────────

test.describe('Visual — mobile viewport', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('home — mobile dark', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.remove('light'));
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-dark-home.png', THRESHOLD);
  });

  test('home — mobile light', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.documentElement.classList.add('light'));
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-light-home.png', THRESHOLD);
  });

  test('pricing — mobile dark', async ({ page }) => {
    await page.goto('/pricing');
    await page.evaluate(() => document.documentElement.classList.remove('light'));
    await waitForPageStable(page);
    await expect(page).toHaveScreenshot('mobile-dark-pricing.png', THRESHOLD);
  });
});

// ── Helpers ───────────────────────────────────────────────────────────────────

async function waitForPageStable(page: import('@playwright/test').Page) {
  // Wait for network idle + any CSS transitions to settle
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(200);
}
