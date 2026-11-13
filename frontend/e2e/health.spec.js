import { test, expect } from '@playwright/test';

test('the browser reaches Spring Boot through the Vite proxy', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Roommate Expenses', exact: true })).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('Backend connected');
});
