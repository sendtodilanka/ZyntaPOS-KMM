import { test as setup, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_STATE = path.join(__dirname, '.auth/state.json');

setup('authenticate', async ({ page }) => {
  // Navigate to login. The MSW mock always returns a user for GET /admin/auth/me,
  // so the app auto-authenticates and redirects to "/" without requiring form input.
  await page.goto('/login');

  // Wait for the app to settle — either it stays on /login or redirects to /
  await page.waitForURL(/^\S*\/$/, { timeout: 90_000 });

  // Persist cookies + localStorage so all authenticated tests reuse this session
  await page.context().storageState({ path: AUTH_STATE });
});
