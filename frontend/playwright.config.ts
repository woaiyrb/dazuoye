import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:4174'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 6_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // 本地复用系统 Chrome；CI 使用 Playwright 安装的 Chromium。
    channel: process.env.CI ? undefined : 'chrome',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer:
    process.env.PLAYWRIGHT_EXTERNAL_SERVER === '1'
      ? undefined
      : {
          command: 'npm run dev -- --port 4174',
          url: 'http://127.0.0.1:4174/login',
          reuseExistingServer: !process.env.CI,
          timeout: 60_000,
        },
})
