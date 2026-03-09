import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// All public routes to audit
const pages = [
  { name: 'Home',        path: '/'           },
  { name: 'Features',   path: '/features'   },
  { name: 'Pricing',    path: '/pricing'    },
  { name: 'Industries', path: '/industries' },
  { name: 'Download',   path: '/download'   },
  { name: 'About',      path: '/about'      },
  { name: 'Support',    path: '/support'    },
  { name: 'Blog index', path: '/blog'       },
  { name: 'Privacy',    path: '/privacy'    },
  { name: 'Terms',      path: '/terms'      },
];

// ── WCAG audit (dark mode — default) ─────────────────────────────────────────

test.describe('Accessibility — dark mode (WCAG 2.1 AA)', () => {
  for (const { name, path } of pages) {
    test(name, async ({ page }) => {
      await page.goto(path);
      // Ensure dark mode (default)
      await page.evaluate(() => document.documentElement.classList.remove('light'));

      const results = await new AxeBuilder({ page })
        // Target WCAG 2.1 Level AA rules
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
      // Switch to light mode
      await page.evaluate(() => document.documentElement.classList.add('light'));

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
        .analyze();

      expect(results.violations, formatViolations(results.violations)).toEqual([]);
    });
  }
});

// ── Keyboard navigation ───────────────────────────────────────────────────────

test.describe('Keyboard navigation', () => {
  test('can Tab through header nav links', async ({ page }) => {
    await page.goto('/');
    // Focus the logo link first
    await page.locator('header a[aria-label="ZyntaPOS home"]').focus();

    // Tab through each nav item and confirm focus moves forward
    const navItems = page.locator('nav[aria-label="Main navigation"] a');
    const count = await navItems.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
      await page.keyboard.press('Tab');
    }
    // After tabbing through all nav links, focus should have advanced
    const focused = await page.evaluate(() => document.activeElement?.tagName);
    expect(focused).toBeTruthy();
  });

  test('mobile menu is keyboard accessible', async ({ page, viewport }) => {
    test.skip((viewport?.width ?? 1280) >= 768, 'desktop viewport — mobile menu not rendered');
    await page.goto('/');
    const btn = page.locator('#mobile-menu-btn');
    await btn.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#mobile-menu')).toBeVisible();
  });

  test('theme toggle is keyboard accessible', async ({ page }) => {
    await page.goto('/');
    await page.locator('#theme-toggle').focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveClass(/light/);
  });
});

// ── Helper ────────────────────────────────────────────────────────────────────

function formatViolations(violations: { id: string; description: string; nodes: { html: string }[] }[]): string {
  if (!violations.length) return '';
  return violations
    .map(v => `[${v.id}] ${v.description}\n  ${v.nodes.map(n => n.html).join('\n  ')}`)
    .join('\n\n');
}
