import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  testMatch: '*.spec.js',
  use: { baseURL: 'http://127.0.0.1:5173', browserName: 'chromium' },
  webServer: {
    command: 'npm --prefix .. run dev',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
  },
});
