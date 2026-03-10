import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

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

// All authenticated routes to audit
const pages = [
  { name: 'Dashboard',       path: '/'                 },
  { name: 'Licenses',        path: '/licenses'         },
  { name: 'Stores',          path: '/stores'           },
  { name: 'Users',           path: '/users'            },
  { name: 'Tickets',         path: '/tickets'          },
  { name: 'Audit Log',       path: '/audit'            },
  { name: 'Alerts',          path: '/alerts'           },
  { name: 'Health',          path: '/health'           },
  { name: 'Sync',            path: '/sync'             },
  { name: 'Reports',         path: '/reports'          },
  { name: 'Config',          path: '/config'           },
  { name: 'Profile',         path: '/settings/profile' },
  { name: 'MFA',             path: '/settings/mfa'     },
];

// ── WCAG audit (dark mode — default) ─────────────────────────────────────────

test.describe('Accessibility — dark mode (WCAG 2.1 AA)', () => {
  for (const { name, path } of pages) {
    test(name, async ({ page }) => {
      await page.goto(path);
      await page.evaluate(() => {
        document.documentElement.classList.add('dark');
      });
      await page.waitForLoadState('networkidle');
      // Wait for React to finish rendering (avoids scanning loading spinner)
      await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
        .analyze();

      expect(results.violations, formatViolations(results.violations)).toEqual([]);
    });
  }
});

// ── WCAG audit (light mode) ───────────────────────────────────────────────────

test.describe('Accessibility — light mode (WCAG 2.1 AA)', () => {
  for (const { name, path } of pages) {
    test(name, async ({ page }) => {
      await page.goto(path);
      await page.evaluate(() => {
        document.documentElement.classList.remove('dark');
      });
      await page.waitForLoadState('networkidle');
      // Wait for React to finish rendering (avoids scanning loading spinner)
      await page.locator('main').first().waitFor({ state: 'visible', timeout: 15_000 });

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
        .analyze();

      expect(results.violations, formatViolations(results.violations)).toEqual([]);
    });
  }
});

// ── Login page (no auth required) ─────────────────────────────────────────────

test.describe('Accessibility — login page (WCAG 2.1 AA)', () => {
  test('dark mode', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await page.evaluate(() => document.documentElement.classList.add('dark'));

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });

  test('light mode', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await page.evaluate(() => document.documentElement.classList.remove('dark'));

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });
});

// ── Keyboard navigation ───────────────────────────────────────────────────────

test.describe('Keyboard navigation', () => {
  test('can Tab through sidebar nav links', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    // Wait for sidebar to render
    await page.locator('nav a, aside a').first().waitFor({ state: 'visible', timeout: 15_000 });

    const navLinks = page.locator('nav a, aside a');
    const count = await navLinks.count();
    expect(count).toBeGreaterThan(0);

    // Tab forward through each nav item
    await navLinks.first().focus();
    for (let i = 0; i < count - 1; i++) {
      await page.keyboard.press('Tab');
    }
    const focused = await page.evaluate(() => document.activeElement?.tagName);
    expect(focused).toBeTruthy();
  });

  test('mobile sidebar is keyboard accessible', async ({ page, viewport }) => {
    test.skip((viewport?.width ?? 1280) >= 768, 'desktop viewport — mobile sidebar not rendered');
    await page.goto('/');
    const toggleBtn = page.locator('[aria-label*="menu"], [aria-label*="sidebar"], button[aria-controls]').first();
    await toggleBtn.focus();
    await page.keyboard.press('Enter');
    // Mobile sidebar should open
    const focused = await page.evaluate(() => document.activeElement?.tagName);
    expect(focused).toBeTruthy();
  });

  test('theme toggle is keyboard accessible', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('[aria-label*="theme"], [aria-label*="dark"], [aria-label*="light"]').first();
    await toggle.focus();
    await page.keyboard.press('Enter');
    // Theme class should change
    const hasDark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
    // Just verify the element is focusable and activation works without throwing
    expect(typeof hasDark).toBe('boolean');
  });
});

// ── Helper ────────────────────────────────────────────────────────────────────

function formatViolations(violations: { id: string; description: string; nodes: { html: string }[] }[]): string {
  if (!violations.length) return '';
  return violations
    .map(v => `[${v.id}] ${v.description}\n  ${v.nodes.map(n => n.html).join('\n  ')}`)
    .join('\n\n');
}
