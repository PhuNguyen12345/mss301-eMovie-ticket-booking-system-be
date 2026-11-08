import { cinemaResponse } from "./cinema-fixtures";
import { test, expect, type Page } from "@playwright/test";

const show = {
  id: 7,
  movieId: 1,
  cinemaId: 1,
  screenId: 1,
  startTime: "2026-11-05T18:00:00",
  endTime: "2026-11-05T20:00:00",
  basePrice: 90000,
  status: "SCHEDULED",
};
const paged = (data: unknown[], page = 0, last = true) => ({
  data,
  page,
  size: 100,
  totalElements: last ? data.length : 2,
  totalPages: last ? 1 : 2,
  first: page === 0,
  last,
});

async function mockApi(page: Page) {
  const state = {
    prices: [{ id: 77, seatTypeId: 2, price: 120000 }],
    putStatus: 200,
    putCalls: 0,
    listCalls: 0,
    moviePages: [] as number[],
    writes: [] as { authorization: string | undefined; body: unknown }[],
    badList: false,
    lists: [] as string[],
  };
  await page.clock.setFixedTime(new Date("2026-11-05T08:00:00+07:00"));
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const req = route.request();
      const url = new URL(req.url());
      const send = (data: unknown, status = 200) =>
        route.fulfill({ status, json: { status, message: "OK", data } });
      const catalogue = cinemaResponse(url.pathname);
      if (catalogue !== undefined) return send(catalogue);
      if (url.pathname === "/api/auth/refresh") return send(null, 401);
      if (url.pathname === "/api/auth/logout") return send(null);
      if (url.pathname === "/api/auth/login")
        return send({
          accessToken: "test-admin-token",
          tokenType: "Bearer",
          expiresIn: 3600,
          user: { userId: 42, fullName: "Quản trị", roles: ["ADMIN"] },
        });
      if (url.pathname === "/api/movies") {
        const current = Number(url.searchParams.get("page"));
        state.moviePages.push(current);
        return send(
          paged(
            [
              {
                id: current + 1,
                title: current === 0 ? "Interstellar" : "Dune: Part Two",
                durationMinutes: 120,
                status: "PRESENTING",
              },
            ],
            current,
            current === 1,
          ),
        );
      }
      if (url.pathname === "/api/showtimes") {
        state.listCalls++;
        state.lists.push(url.search);
        return state.badList ? send({ unexpected: [] }) : send(paged([show]));
      }
      if (url.pathname === "/api/showtimes/7/seat-prices") {
        if (req.method() === "GET") return send(state.prices);
        state.putCalls++;
        state.writes.push({
          authorization: req.headers().authorization,
          body: req.postDataJSON(),
        });
        if (state.putStatus !== 200) return send(null, state.putStatus);
        state.prices = (
          req.postDataJSON() as { seatTypeId: number; price: number }[]
        ).map((price, index) => ({ ...price, id: 77 + index }));
        return send(state.prices);
      }
      return send(null, 404);
    },
  );
  await page.goto("/admin/showtimes");
  await login(page);
  await expect(page.getByLabel("Giá VIP", { exact: true })).toBeVisible();
  await expect(
    page.getByLabel("Phim", { exact: true }).locator("option"),
  ).toHaveCount(3);
  return state;
}
async function login(page: Page) {
  await page
    .locator(".topbar")
    .getByRole("button", { name: "Đăng nhập", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Email").fill("admin@example.com");
  await dialog.getByLabel("Mật khẩu").fill("demo-password");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(dialog).not.toBeVisible();
}

test("admin location filtering clears dependent venue selections", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.getByLabel("Rạp", { exact: true }).selectOption("1");
  await page.getByLabel("Phòng chiếu", { exact: true }).selectOption("1");
  await expect
    .poll(() => new URLSearchParams(state.lists.at(-1)).get("screenId"))
    .toBe("1");
  await page.getByLabel("Địa điểm", { exact: true }).fill("  Hồ Chí Minh  ");
  await page.getByRole("button", { name: "Lọc địa điểm" }).click();
  await expect
    .poll(() => new URLSearchParams(state.lists.at(-1)).get("location"))
    .toBe("Hồ Chí Minh");
  const params = new URLSearchParams(state.lists.at(-1));
  expect(params.has("cinemaId")).toBe(false);
  expect(params.has("screenId")).toBe(false);
  expect(params.get("page")).toBe("0");
  await expect(page.getByLabel("Rạp", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Phòng chiếu", { exact: true })).toHaveValue("");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("HTTP mode follows catalogue pagination and sends server-side filters", async ({
  page,
}) => {
  const state = await mockApi(page);
  // StrictMode can cancel the initial read and start it again.
  expect([...new Set(state.moviePages)]).toEqual([0, 1]);
  await page.getByLabel("Phim", { exact: true }).selectOption("2");
  await expect
    .poll(() => state.lists.some((query) => query.includes("movieId=2")))
    .toBe(true);
  await expect(
    page.getByRole("button", { name: "Tạo suất chiếu", exact: true }),
  ).toBeEnabled();
  await expect(page.getByText("Dữ liệu demo")).not.toBeVisible();
});

test("failed writes preserve the draft; successful writes update cache and invalidate lists", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.getByLabel("Giá VIP", { exact: true }).fill("155000");
  state.putStatus = 503;
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Dịch vụ đang gặp sự cố");
  expect(state.putCalls).toBe(1);
  await expect(page.getByLabel("Giá VIP", { exact: true })).toHaveValue(
    "155000",
  );
  expect(state.writes[0]).toEqual({
    authorization: "Bearer test-admin-token",
    body: [{ seatTypeId: 2, price: 155000 }],
  });
  state.putStatus = 200;
  const previous = state.listCalls;
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đã cập nhật");
  await expect.poll(() => state.listCalls).toBeGreaterThan(previous);
  await expect(
    page.getByRole("button", { name: "Lưu bảng giá", exact: true }),
  ).toBeDisabled();
  expect(
    await page.evaluate(
      () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
    ),
  ).not.toContain("test-admin-token");
});

test("401 clears the session without replaying the write", async ({ page }) => {
  const state = await mockApi(page);
  await page.getByLabel("Giá VIP", { exact: true }).fill("155000");
  state.putStatus = 401;
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Đăng nhập để tiếp tục" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Lưu bảng giá", exact: true }),
  ).not.toBeVisible();
  expect(state.putCalls).toBe(1);
});

test("invalid server data is visible as an error and never replaced by demo fixtures", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.badList = true;
  await page.getByRole("button", { name: "Làm mới lịch chiếu" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Dữ liệu trả về không đúng định dạng",
  );
  await expect(page.locator(".show-row")).toHaveCount(0);
  state.badList = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator(".show-row")).toHaveCount(1);
});
