import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  projects: [
    {
      name: "demo",
      testMatch: "demo.spec.ts",
      use: { baseURL: "http://127.0.0.1:5173" },
    },
    {
      name: "api",
      testMatch: "api.spec.ts",
      use: { baseURL: "http://127.0.0.1:5174" },
    },
  ],
  use: {
    baseURL: "http://127.0.0.1:5173",
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }
      : {},
  },
  webServer: [
    {
      command: "npm run dev -- --port 5173 --strictPort",
      url: "http://127.0.0.1:5173",
      reuseExistingServer: !process.env.CI,
    },
    {
      command: "npm run dev -- --port 5174 --strictPort",
      env: { VITE_DATA_MODE: "api", VITE_API_BASE_URL: "/api" },
      url: "http://127.0.0.1:5174",
      reuseExistingServer: !process.env.CI,
    },
  ],
});
