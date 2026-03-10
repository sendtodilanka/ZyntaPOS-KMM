import { test as setup, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_STATE = path.join(__dirname, '.auth/state.json');

setup('authenticate', async ({ page }) => {
  await page.goto('/login');

  // Wait for the login form to be ready
  await expect(page.getByRole('button', { name: /sign in|log in/i })).toBeVisible();

  // Fill credentials (MSW mock accepts any — use env vars in real CI)
  await page.getByLabel(/email/i).fill(process.env.E2E_EMAIL ?? 'admin@zyntapos.com');
  await page.getByLabel(/password/i).fill(process.env.E2E_PASSWORD ?? 'password');
  await page.getByRole('button', { name: /sign in|log in/i }).click();

  // Confirm navigation to dashboard
  await expect(page).toHaveURL('/', { timeout: 10_000 });

  // Persist cookies + localStorage so all authenticated tests reuse this session
  await page.context().storageState({ path: AUTH_STATE });
});
