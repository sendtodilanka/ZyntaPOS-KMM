import { test as setup } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_STATE = path.join(__dirname, '.auth/state.json');

setup('authenticate', async ({ page }) => {
  // MSW always returns an authenticated user in mock mode.
  // Navigate to the dashboard and wait for the main content to render.
  await page.goto('/');

  // Wait for the main content area to be visible (React render complete)
  await page.locator('main').first().waitFor({ state: 'visible', timeout: 90_000 });

  // Persist cookies + localStorage so all authenticated tests reuse this session
  await page.context().storageState({ path: AUTH_STATE });
});
