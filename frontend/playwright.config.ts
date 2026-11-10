import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests: a real browser against the whole stack (web client, API,
 * PostgreSQL, Redis, Kafka, the notifications service and its mail catcher).
 * Start the stack first, `docker compose up --build` in the repository root,
 * then `npm run e2e` here. Addresses can be changed through the E2E_* variables.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The tests share one cinema and its seats; run them one after another.
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Set PLAYWRIGHT_CHANNEL=chrome to use an installed Chrome instead of downloading a browser.
        channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
      },
    },
  ],
});
