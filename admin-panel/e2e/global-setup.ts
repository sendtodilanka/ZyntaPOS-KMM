import { test as setup, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_STATE = path.join(__dirname, '.auth/state.json');

setup('authenticate', async ({ page }) => {
  // Set the mock auth cookie so the MSW handler for GET /admin/auth/me returns
  // a user. Tests that call clearCookies() will remove this cookie, causing
  // /me to return 401 and the app to redirect to /login (unauthenticated state).
  await page.context().addCookies([
    {
      name: 'admin_access_token',
      value: 'mock-token',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
    },
  ]);

  await page.goto('/');

  // Wait for the main content area to be visible (auth + React render complete)
  await page.locator('main').first().waitFor({ state: 'visible', timeout: 90_000 });

  // Persist cookies + localStorage so all authenticated tests reuse this session
  await page.context().storageState({ path: AUTH_STATE });
});
