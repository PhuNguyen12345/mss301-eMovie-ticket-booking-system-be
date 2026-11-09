import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  workers: 4,
  projects: [
    {
      name: "demo",
      testMatch: "demo.spec.ts",
      use: { baseURL: "http://127.0.0.1:5183" },
    },
    {
      name: "api",
      testMatch: /(?:^|[./])(?:.*\.)?api\.spec\.ts$/,
      use: { baseURL: "http://127.0.0.1:5184" },
    },
  ],
  use: {
    baseURL: "http://127.0.0.1:5183",
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
  webServer: [
    {
      command: "npm run dev -- --port 5183 --strictPort",
      env: { VITE_DATA_MODE: "demo" },
      url: "http://127.0.0.1:5183",
      reuseExistingServer: false,
    },
    {
      command: "npm run dev -- --port 5184 --strictPort",
      env: { VITE_DATA_MODE: "api", VITE_API_BASE_URL: "/api" },
      url: "http://127.0.0.1:5184",
      reuseExistingServer: false,
    },
  ],
});
