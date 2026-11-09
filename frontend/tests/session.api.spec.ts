import { expect, test, type BrowserContext, type Page } from "@playwright/test";

async function setup(context: BrowserContext) {
  const state = {
    credential: "",
    refreshes: 0,
    restoreStatus: 200,
    logoutStatus: 200,
  };
  await context.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const send = (data: unknown, status = 200, cookie?: string) =>
        route.fulfill({
          status,
          headers:
            cookie === undefined
              ? {}
              : {
                  "Set-Cookie": `emovie_refresh=${cookie}; HttpOnly; SameSite=Strict; Path=/api/auth; Max-Age=${cookie ? 3600 : 0}`,
                },
          json: { status, message: "OK", data },
        });
      if (url.pathname === "/api/auth/logout") {
        if (state.logoutStatus !== 200) return send(null, state.logoutStatus);
        state.credential = "";
        return send(null, 200, "");
      }
      if (
        url.pathname === "/api/auth/login" ||
        url.pathname === "/api/auth/refresh"
      ) {
        expect(url.searchParams.get("transport")).toBe("cookie");
        if (url.pathname.endsWith("refresh")) {
          if (state.restoreStatus !== 200)
            return send(null, state.restoreStatus);
          const headers = await request.allHeaders();
          if (
            !state.credential ||
            !headers.cookie?.includes(`emovie_refresh=${state.credential}`)
          )
            return send(null, 401);
          state.refreshes++;
        }
        state.credential = `cookie-${state.refreshes}-${Date.now()}`;
        return send(
          {
            accessToken: `access-${state.refreshes}`,
            tokenType: "Bearer",
            expiresIn: 900,
            refreshToken: null,
            refreshExpiresIn: 3600,
            user: { userId: 1, fullName: "Session Admin", roles: ["ADMIN"] },
          },
          200,
          state.credential,
        );
      }
      if (url.pathname === "/api/bookings/me") {
        expect(request.headers().authorization).toMatch(/^Bearer access-/);
        return send({
          data: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        });
      }
      return send(null, 404);
    },
  );
  return state;
}

async function login(page: Page) {
  await page.goto("/bookings");
  await page
    .locator(".topbar")
    .getByRole("button", { name: "Đăng nhập", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Email").fill("admin@example.test");
  await dialog.getByLabel("Mật khẩu", { exact: true }).fill("password");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
}

test("reload and payment return restore the cookie session without browser storage tokens", async ({
  page,
  context,
}) => {
  const state = await setup(context);
  await login(page);
  await page.reload();
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
  expect(state.refreshes).toBe(1);
  await page.goto("/payment/result");
  await expect(
    page.getByRole("button", { name: "Đăng xuất", exact: true }),
  ).toBeVisible();
  expect(state.refreshes).toBe(2);
  expect(await page.evaluate(() => document.cookie)).not.toContain(
    "emovie_refresh",
  );
  expect(
    await page.evaluate(
      () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
    ),
  ).not.toMatch(/access-|cookie-/);
  expect(
    (await context.cookies()).find(
      (cookie) => cookie.name === "emovie_refresh",
    ),
  ).toMatchObject({ httpOnly: true, sameSite: "Strict" });
});

test("concurrent tabs serialize cookie rotation and logout reaches every tab", async ({
  page,
  context,
}) => {
  const state = await setup(context);
  await login(page);
  const other = await context.newPage();
  await other.goto("/bookings");
  await expect(other.getByText("Chưa có vé", { exact: true })).toBeVisible();
  await Promise.all([page.reload(), other.reload()]);
  for (const tab of [page, other])
    await expect(tab.getByText("Chưa có vé", { exact: true })).toBeVisible();
  expect(state.refreshes).toBe(3);
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  for (const tab of [page, other])
    await expect(
      tab.getByRole("heading", { name: "Đăng nhập để tiếp tục" }),
    ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Đăng nhập để tiếp tục" }),
  ).toBeVisible();
  expect(
    (await context.cookies()).some(
      (cookie) => cookie.name === "emovie_refresh",
    ),
  ).toBe(false);
});

test("temporary restoration failure offers retry without asking for credentials", async ({
  page,
  context,
}) => {
  const state = await setup(context);
  await login(page);
  state.restoreStatus = 503;
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("Dịch vụ đang gặp sự cố");
  await expect(
    page.getByRole("heading", { name: "Đăng nhập để tiếp tục" }),
  ).toHaveCount(0);
  state.restoreStatus = 200;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
});

test("failed logout remains visible until server revocation succeeds", async ({
  page,
  context,
}) => {
  const state = await setup(context);
  await login(page);
  state.logoutStatus = 503;
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Dịch vụ đang gặp sự cố");
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
  state.logoutStatus = 200;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Đăng nhập để tiếp tục" }),
  ).toBeVisible();
});
