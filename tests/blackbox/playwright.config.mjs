import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './system', fullyParallel: false, workers: 1, retries: 0,
  timeout: 30000,
  reporter: [['list'], ['json', { outputFile: process.env.BB_BROWSER_RESULTS }]],
  outputDir: process.env.BB_BROWSER_ARTIFACTS || '.artifacts/browser',
  use: { baseURL: process.env.BB_WEB_URL || 'http://127.0.0.1:18081', browserName: 'chromium',
    trace: 'retain-on-failure', screenshot: 'only-on-failure' },
});
