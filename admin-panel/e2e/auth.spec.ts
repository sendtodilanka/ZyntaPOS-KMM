import { test, expect } from '@playwright/test';

/**
 * Stub /admin/auth/me to return 401 (unauthenticated) and /admin/auth/status
 * to return needsBootstrap: false.  Patches window.fetch before MSW service
 * worker activates.
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

/**
 * Stub all auth endpoints: /me returns 401, /status returns needsBootstrap
 * false, and /login returns 401 for any credentials (simulates invalid login).
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
 * Stub /login to succeed and /me to return authenticated user afterward.
 * Uses a simple flag so /me returns 401 until login is called, then 200.
 */
async function stubLoginSuccess(page: import('@playwright/test').Page) {
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

    // Track whether login has succeeded
    (window as unknown as Record<string, boolean>).__loggedIn = false;

    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : (input instanceof URL ? input.href : (input as Request).url);

      if (url.includes('/admin/auth/status')) {
        return Promise.resolve(new Response(JSON.stringify({ needsBootstrap: false }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }

      if (url.includes('/admin/auth/login') && init?.method?.toUpperCase() === 'POST') {
        (window as unknown as Record<string, boolean>).__loggedIn = true;
        return Promise.resolve(new Response(JSON.stringify(mockUser), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }

      if (url.includes('/admin/auth/me')) {
        if ((window as unknown as Record<string, boolean>).__loggedIn) {
          return Promise.resolve(new Response(JSON.stringify(mockUser), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }));
        }
        return Promise.resolve(new Response(JSON.stringify({ error: 'Unauthorized' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }));
      }

      return _orig(input, init);
    };
  });
}

// ── Login page rendering ─────────────────────────────────────────────────────

test.describe('Auth — login page rendering', () => {
  test.beforeEach(async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
  });

  test('displays sign-in heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
  });

  test('displays email input with correct attributes', async ({ page }) => {
    const emailInput = page.locator('input#email');
    await expect(emailInput).toBeVisible();
    await expect(emailInput).toHaveAttribute('type', 'email');
    await expect(emailInput).toHaveAttribute('autocomplete', 'email');
  });

  test('displays password input with correct attributes', async ({ page }) => {
    const passwordInput = page.locator('input#password');
    await expect(passwordInput).toBeVisible();
    await expect(passwordInput).toHaveAttribute('type', 'password');
    await expect(passwordInput).toHaveAttribute('autocomplete', 'current-password');
  });

  test('displays sign-in submit button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeEnabled();
  });

  test('displays password visibility toggle', async ({ page }) => {
    const toggle = page.getByRole('button', { name: /show password|hide password/i });
    await expect(toggle).toBeVisible();

    // Initially password is hidden
    await expect(page.locator('input#password')).toHaveAttribute('type', 'password');

    // Click toggle to reveal
    await toggle.click();
    await expect(page.locator('input#password')).toHaveAttribute('type', 'text');

    // Click toggle to hide again
    await toggle.click();
    await expect(page.locator('input#password')).toHaveAttribute('type', 'password');
  });
});

// ── Validation errors ────────────────────────────────────────────────────────

test.describe('Auth — validation errors', () => {
  test('shows validation error for empty email', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Leave email empty, fill password
    await page.locator('input#password').fill('somepassword');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Zod validation: "Enter a valid email address"
    await expect(page.locator('text=Enter a valid email address')).toBeVisible({ timeout: 5_000 });
  });

  test('shows validation error for empty password', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Fill email, leave password empty
    await page.locator('input#email').fill('admin@zyntapos.com');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Zod validation: "Password is required"
    await expect(page.locator('text=Password is required')).toBeVisible({ timeout: 5_000 });
  });

  test('shows validation error for invalid email format', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.locator('input#email').fill('not-an-email');
    await page.locator('input#password').fill('somepassword');
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.locator('text=Enter a valid email address')).toBeVisible({ timeout: 5_000 });
  });

  test('shows both validation errors when form is submitted empty', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.locator('text=Enter a valid email address')).toBeVisible({ timeout: 5_000 });
    await expect(page.locator('text=Password is required')).toBeVisible({ timeout: 5_000 });
  });
});

// ── Invalid credentials ──────────────────────────────────────────────────────

test.describe('Auth — invalid credentials', () => {
  test('shows server error for wrong email/password', async ({ page }) => {
    await stubLoginFailure(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.locator('input#email').fill('wrong@example.com');
    await page.locator('input#password').fill('wrongpassword');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Server error banner: "Invalid email or password."
    await expect(page.locator('text=Invalid email or password')).toBeVisible({ timeout: 10_000 });
  });
});

// ── Successful login ─────────────────────────────────────────────────────────

test.describe('Auth — successful login', () => {
  test('redirects to dashboard after successful login', async ({ page }) => {
    await stubLoginSuccess(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.locator('input#email').fill('admin@zyntapos.com');
    await page.locator('input#password').fill('correctpassword');
    await page.getByRole('button', { name: /sign in/i }).click();

    // After successful login the auth guard in __root.tsx redirects to /
    await expect(page).toHaveURL('/', { timeout: 15_000 });
  });
});

// ── Auth guard redirect ──────────────────────────────────────────────────────

test.describe('Auth — guard redirect', () => {
  test('unauthenticated user visiting / is redirected to /login', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('unauthenticated user visiting protected route is redirected to /login', async ({ page }) => {
    await stubUnauthenticated(page);
    await page.goto('/licenses');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });
});
