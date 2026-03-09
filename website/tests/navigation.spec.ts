import { test, expect } from '@playwright/test';

// ── Main nav links ────────────────────────────────────────────────────────────

const navLinks = [
  { label: 'Features',   href: '/features'   },
  { label: 'Pricing',    href: '/pricing'     },
  { label: 'Industries', href: '/industries'  },
  { label: 'Download',   href: '/download'    },
  { label: 'About',      href: '/about'       },
  { label: 'Support',    href: '/support'     },
];

test.describe('Header navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('logo navigates to home', async ({ page }) => {
    await page.goto('/features');
    await page.click('a[aria-label="ZyntaPOS home"]');
    await expect(page).toHaveURL('/');
  });

  for (const link of navLinks) {
    test(`desktop nav: "${link.label}" → ${link.href}`, async ({ page }) => {
      const navLink = page.locator('nav[aria-label="Main navigation"] a', { hasText: link.label });
      await navLink.click();
      await expect(page).toHaveURL(link.href);
    });
  }

  test('"Download Free" header CTA → /pricing', async ({ page, viewport }) => {
    // The header CTA is hidden below sm (640px) — skip on mobile viewports
    test.skip((viewport?.width ?? 1280) < 640, 'CTA hidden on mobile viewports');
    // The header CTA (hidden on mobile) links to /pricing
    const cta = page.locator('header a[href="/pricing"]').first();
    await expect(cta).toBeVisible();
    await cta.click();
    await expect(page).toHaveURL('/pricing');
  });
});

// ── Hero CTAs ─────────────────────────────────────────────────────────────────

test.describe('Hero CTAs', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('"Download Free" hero CTA → /download', async ({ page }) => {
    const cta = page.locator('section a[href="/download"]').first();
    await cta.click();
    await expect(page).toHaveURL('/download');
  });

  test('"See All Features" hero CTA → /features', async ({ page }) => {
    const cta = page.locator('section a[href="/features"]').first();
    await cta.click();
    await expect(page).toHaveURL('/features');
  });
});

// ── Mobile menu ───────────────────────────────────────────────────────────────

test.describe('Mobile menu', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('opens and closes', async ({ page }) => {
    await page.goto('/');
    const btn  = page.locator('#mobile-menu-btn');
    const menu = page.locator('#mobile-menu');

    // Initially hidden
    await expect(menu).toBeHidden();
    await expect(btn).toHaveAttribute('aria-expanded', 'false');

    // Open
    await btn.click();
    await expect(menu).toBeVisible();
    await expect(btn).toHaveAttribute('aria-expanded', 'true');

    // Close
    await btn.click();
    await expect(menu).toBeHidden();
    await expect(btn).toHaveAttribute('aria-expanded', 'false');
  });

  test('mobile menu links navigate correctly', async ({ page }) => {
    await page.goto('/');
    await page.locator('#mobile-menu-btn').click();
    await page.locator('#mobile-menu a[href="/pricing"]').click();
    await expect(page).toHaveURL('/pricing');
  });
});

// ── Theme toggle ──────────────────────────────────────────────────────────────

test.describe('Theme toggle', () => {
  test('switches between dark and light mode', async ({ page }) => {
    await page.goto('/');
    const html   = page.locator('html');
    const toggle = page.locator('#theme-toggle');

    // Default: dark mode (no .light class)
    await expect(html).not.toHaveClass(/light/);

    // Switch to light
    await toggle.click();
    await expect(html).toHaveClass(/light/);

    // Switch back to dark
    await toggle.click();
    await expect(html).not.toHaveClass(/light/);
  });

  test('persists theme choice in localStorage', async ({ page }) => {
    await page.goto('/');
    await page.locator('#theme-toggle').click();

    const stored = await page.evaluate(() => localStorage.getItem('zynta-theme'));
    expect(stored).toBe('light');
  });
});

// ── 404 page ──────────────────────────────────────────────────────────────────

test('404 page renders for unknown route', async ({ page }) => {
  const res = await page.goto('/this-does-not-exist-xyz');
  expect(res?.status()).toBe(404);
  // Should still render a page (not blank)
  await expect(page.locator('body')).not.toBeEmpty();
});
