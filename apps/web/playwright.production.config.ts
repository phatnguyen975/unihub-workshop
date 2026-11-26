import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/journeys",
  testMatch: "workshop-production.spec.ts",
  workers: 1,
  reporter: "list",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://127.0.0.1:4174",
    viewport: { width: 1440, height: 900 },
  },
  webServer: {
    command:
      "node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4174 --strictPort",
    url: "http://127.0.0.1:4174",
    reuseExistingServer: false,
    gracefulShutdown: { signal: "SIGINT", timeout: 1_000 },
    timeout: 30_000,
  },
});
