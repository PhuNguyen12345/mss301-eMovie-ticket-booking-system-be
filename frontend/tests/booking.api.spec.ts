import { cinemaResponse } from "./cinema-fixtures";
import { test, expect, type Page } from "@playwright/test";
const movie = {
  id: 1,
  title: "Interstellar",
  description: "Hành trình giữa các vì sao.",
  durationMinutes: 120,
  status: "PRESENTING",
  ageRating: "T13",
  language: "Tiếng Anh",
  posterUrl: null,
  trailerUrl: null,
  releaseDate: "2026-11-01",
  genres: [],
};
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
const paged = (data: unknown[]) => ({
  data,
  page: 0,
  size: 10,
  totalElements: data.length,
  totalPages: data.length ? 1 : 0,
  first: true,
  last: true,
});
async function mockApi(page: Page) {
  const state = {
    roles: ["CUSTOMER"],
    userId: 7,
    seatsStatus: 200,
    holdStatus: 201,
    payStatus: 200,
    syncStatus: 200,
    holdCalls: 0,
    payCalls: 0,
    syncCalls: 0,
    movieWrites: [] as unknown[],
    refundCalls: 0,
    writes: [] as { path: string; body: unknown; authorization?: string }[],
    requests: [] as string[],
    confirmPayment: false,
    checkoutUrl: "https://pay.payos.vn/web/test",
    booking: {
      id: 12,
      bookingCode: "BK-TEST-12",
      userId: 7,
      showtimeId: 7,
      status: "PENDING_PAYMENT",
      totalAmount: 95000,
      bookingTime: "2026-11-05T08:00:00",
      expiredAt: "2026-11-05T08:05:00",
      seats: [{ seatId: 101, price: 95000, status: "HELD" }],
      payment: null as unknown,
    },
    seats: [
      {
        seatId: 101,
        rowCode: "A",
        seatNumber: 1,
        seatTypeId: 1,
        price: 90000,
        status: "AVAILABLE",
      },
      {
        seatId: 102,
        rowCode: "A",
        seatNumber: 2,
        seatTypeId: 1,
        price: 90000,
        status: "BOOKED",
      },
      {
        seatId: 103,
        rowCode: "B",
        seatNumber: 1,
        seatTypeId: 2,
        price: 120000,
        status: "HELD",
      },
    ],
  };
  await page.clock.setFixedTime(new Date("2026-11-05T08:00:00+07:00"));
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname;
      state.requests.push(path);
      const send = (data: unknown, status = 200, message = "OK") =>
        route.fulfill({ status, json: { status, message, data } });
      if (request.method() !== "GET")
        state.writes.push({
          path,
          body: request.postData() ? request.postDataJSON() : null,
          authorization: request.headers().authorization,
        });
      const catalogue = cinemaResponse(url.pathname);
      if (catalogue !== undefined) return send(catalogue);
      if (path === "/api/auth/login")
        return send({
          accessToken: `token-${state.userId}`,
          tokenType: "Bearer",
          expiresIn: 3600,
          user: {
            userId: state.userId,
            fullName: "Khách hàng",
            roles: state.roles,
          },
        });
      if (path === "/api/movies") {
        if (request.method() === "POST") {
          state.movieWrites.push(request.postDataJSON());
          return send({ id: 2, ...request.postDataJSON() }, 201);
        }
        return send(paged([movie]));
      }
      if (path === "/api/movies/1") {
        if (request.method() === "PUT") {
          state.movieWrites.push(request.postDataJSON());
          return send({ ...movie, ...request.postDataJSON() });
        }
        return send(movie);
      }
      if (path === "/api/showtimes") return send(paged([show]));
      if (path === "/api/showtimes/7") return send(show);
      if (path === "/api/bookings/showtimes/7/seats")
        return state.seatsStatus === 200
          ? send({ showtimeId: 7, screenId: 1, seats: state.seats })
          : send(null, state.seatsStatus);
      if (path === "/api/bookings" && request.method() === "POST") {
        state.holdCalls++;
        state.seats[0].status = "HELD";
        return state.holdStatus === 201
          ? send(state.booking, 201)
          : send(null, state.holdStatus, "Ghế vừa được người khác giữ.");
      }
      if (path === "/api/bookings/me")
        return send(paged(state.userId === 7 ? [state.booking] : []));
      if (path === "/api/bookings/12") return send(state.booking);
      if (path === "/api/bookings/12/cancel") {
        state.booking.status = "CANCELLED";
        return send(state.booking);
      }
      if (path === "/api/bookings/12/payments") {
        state.payCalls++;
        if (state.payStatus !== 200) return send(null, state.payStatus);
        state.booking.payment = {
          id: 30,
          bookingId: 12,
          orderCode: 1201,
          provider: "PAYOS",
          amount: 95000,
          status: "PENDING",
          checkoutUrl: state.checkoutUrl,
          paidAt: null,
        };
        return send(state.booking.payment);
      }
      if (path === "/api/payments/bookings/12/sync") {
        state.syncCalls++;
        if (state.syncStatus !== 200) return send(null, state.syncStatus);
        if (state.confirmPayment) {
          state.booking.status = "CONFIRMED";
          state.booking.seats[0].status = "BOOKED";
        }
        return send(state.booking);
      }
      if (path === "/api/users/me")
        return send({
          userId: state.userId,
          fullName: "Khách hàng",
          email: "user@example.com",
          phone: null,
          dateOfBirth: null,
          gender: null,
          cityProvince: null,
          district: null,
          address: null,
          preferredCinemaId: null,
          avatarUrl: null,
        });
      if (path === "/api/users/me/password/otp")
        return send(
          {
            expiresAt: "2026-11-05T01:05:00Z",
            resendAvailableAt: "2026-11-05T01:01:00Z",
            otpDeliveryStatus: "PENDING",
          },
          202,
        );
      if (path === "/api/users/me/password/confirm")
        return send({ changedAt: "2026-11-05T01:00:00Z" });
      if (path === "/api/payments/refunds")
        return send([
          {
            id: 1,
            paymentId: 30,
            bookingId: 12,
            orderCode: 1201,
            amount: 95000,
            reason: "Thanh toán trễ",
            status: state.refundCalls ? "COMPLETED" : "PENDING",
            createdAt: "2026-11-05T07:00:00",
            refundedAt: null,
          },
        ]);
      if (path === "/api/payments/refunds/1/complete") {
        state.refundCalls++;
        return send({
          id: 1,
          paymentId: 30,
          bookingId: 12,
          orderCode: 1201,
          amount: 95000,
          reason: "Thanh toán trễ",
          status: "COMPLETED",
          createdAt: "2026-11-05T07:00:00",
          refundedAt: "2026-11-05T08:00:00",
        });
      }
      return send(null, 404);
    },
  );
  return state;
}
async function login(page: Page) {
  await page
    .locator(".topbar")
    .getByRole("button", { name: "Đăng nhập", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Email").fill("user@example.com");
  await dialog.getByLabel("Mật khẩu", { exact: true }).fill("test-password");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(dialog).not.toBeVisible();
}

test("customer can browse, hold seats, use the server total, and reconcile after PayOS", async ({
  page,
}, testInfo) => {
  const state = await mockApi(page);
  await page.goto("/");
  await expect(page.locator(".movie-card")).toHaveCount(1);
  await page.screenshot({
    path: testInfo.outputPath("catalogue-desktop.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: /Interstellar/ }).click();
  await expect(
    page.getByRole("heading", { name: "Interstellar", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: /Chọn ghế/ }).click();
  await expect(page.getByRole("button", { name: /Ghế A2/ })).toBeDisabled();
  await expect(page.getByRole("button", { name: /Ghế B1/ })).toBeDisabled();
  await page.getByRole("button", { name: /Ghế A1/ }).click();
  await page.getByRole("button", { name: "Đăng nhập để đặt vé" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Email").fill("user@example.com");
  await dialog.getByLabel("Mật khẩu", { exact: true }).fill("test-password");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await page.getByRole("button", { name: "Giữ ghế & tiếp tục" }).click();
  await expect(page).toHaveURL(/\/bookings\/12$/);
  await expect(page.locator(".booking-reference")).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("booking-desktop.png"),
    fullPage: true,
  });
  await expect(page.locator(".summary-lines .total")).toContainText("95.000");
  expect(state.holdCalls).toBe(1);
  expect(state.writes.find((w) => w.path === "/api/bookings")).toEqual({
    path: "/api/bookings",
    body: { showtimeId: 7, seatIds: [101] },
    authorization: "Bearer token-7",
  });
  await page.route("https://pay.payos.vn/**", (route) =>
    route.fulfill({
      contentType: "text/html",
      body: "<h1>Mock PayOS checkout</h1>",
    }),
  );
  await page.getByRole("button", { name: "Thanh toán qua PayOS" }).click();
  await expect(page).toHaveURL("https://pay.payos.vn/web/test");
  state.confirmPayment = true;
  await page.goto("/payment/result?orderCode=1201&status=PAID");
  await login(page);
  await expect(page.getByText("Đã thanh toán", { exact: true })).toBeVisible();
  expect(state.payCalls).toBe(1);
  expect(state.syncCalls).toBe(1);
  await page
    .getByRole("navigation", { name: "Điều hướng chính" })
    .getByRole("link", { name: "Vé của tôi" })
    .click();
  await expect(page.locator(".booking-card")).toContainText("BK-TEST-12");
});

test("a PAID return parameter never confirms a pending booking", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.goto("/payment/result?orderCode=1201&status=PAID&cancel=false");
  await login(page);
  await expect.poll(() => state.syncCalls).toBe(1);
  await expect(page.getByText("Chờ thanh toán", { exact: true })).toBeVisible();
  await expect(
    page.getByText("Đã thanh toán", { exact: true }),
  ).not.toBeVisible();
  state.confirmPayment = true;
  await page.getByRole("button", { name: "Kiểm tra trạng thái" }).click();
  await expect(page.getByText("Đã thanh toán", { exact: true })).toBeVisible();
  expect(state.syncCalls).toBe(2);
});

test("seat conflicts refresh availability without retrying the hold", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.holdStatus = 409;
  await page.goto("/showtimes/7/seats");
  await login(page);
  await page.getByRole("button", { name: /Ghế A1/ }).click();
  await page.getByRole("button", { name: "Giữ ghế & tiếp tục" }).click();
  await expect(
    page.getByText("Ghế vừa được người khác giữ.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Ghế A1/ })).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Giữ ghế & tiếp tục" }),
  ).toBeDisabled();
  expect(state.holdCalls).toBe(1);
});

test("unavailable cinema dependency never generates fake seats", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.seatsStatus = 503;
  await page.goto("/showtimes/7/seats");
  await expect(page.getByText(/Chưa thể tải sơ đồ ghế/)).toBeVisible();
  await expect(page.locator(".seat")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Đăng nhập để đặt vé" }),
  ).toBeDisabled();
  state.seatsStatus = 200;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator(".seat")).toHaveCount(3);
});

test("expired holds cannot initiate payment; failed payment creation is not replayed", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.payStatus = 502;
  await page.goto("/bookings/12");
  await login(page);
  await page.getByRole("button", { name: "Thanh toán qua PayOS" }).click();
  await expect(page.getByRole("alert")).toContainText("Dịch vụ đang gặp sự cố");
  expect(state.payCalls).toBe(1);
  await page.clock.setFixedTime(new Date("2026-11-05T08:06:00+07:00"));
  await expect(
    page.getByRole("button", { name: "Thanh toán qua PayOS" }),
  ).toBeDisabled();
});

test("logout clears private data and customer role cannot open admin routes", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.goto("/bookings");
  await login(page);
  await expect(page.locator(".booking-card")).toContainText("BK-TEST-12");
  await page.getByRole("button", { name: "Đăng xuất" }).click();
  await expect(page.locator(".booking-card")).toHaveCount(0);
  state.userId = 8;
  await login(page);
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
  await page.goto("/admin/movies");
  await login(page);
  await expect(
    page.getByRole("heading", { name: "Không có quyền truy cập" }),
  ).toBeVisible();
  expect(state.movieWrites).toHaveLength(0);
  expect(
    await page.evaluate(
      () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
    ),
  ).not.toContain("token-");
});

test("cancel requires confirmation and the backend determines final state", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.goto("/bookings/12");
  await login(page);
  await page.getByRole("button", { name: "Hủy đặt vé", exact: true }).click();
  expect(state.writes.some((w) => w.path.endsWith("/cancel"))).toBe(false);
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Xác nhận hủy" })
    .click();
  await expect(page.getByText("Đã hủy", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Thanh toán qua PayOS" }),
  ).not.toBeVisible();
});

test("admin edits movies using the existing API and placeholders never call absent APIs", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.roles = ["ADMIN"];
  await page.goto("/admin/movies");
  await login(page);
  await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Tên phim")
    .fill("Interstellar - Bản mới");
  await page.getByRole("button", { name: "Lưu phim" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  expect(state.movieWrites[0]).toMatchObject({
    title: "Interstellar - Bản mới",
    durationMinutes: 120,
  });
  await page.getByRole("link", { name: "Thống kê" }).click();
  await expect(page.getByText("Sắp có", { exact: true })).toBeVisible();
  expect(
    state.requests.some((path) =>
      /\/api\/(cinemas|screens|seat-types)/.test(path),
    ),
  ).toBe(false);
});

test("refund completion records a manual transfer only after confirmation", async ({
  page,
}) => {
  const state = await mockApi(page);
  state.roles = ["ADMIN"];
  await page.goto("/admin/refunds");
  await login(page);
  await page.getByRole("button", { name: "Ghi nhận đã hoàn" }).click();
  expect(state.refundCalls).toBe(0);
  await expect(page.getByRole("dialog")).toContainText(
    "không thực hiện chuyển tiền",
  );
  await page
    .getByRole("button", { name: "Đã chuyển tiền", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  expect(state.refundCalls).toBe(1);
});

test("customer pages fit a narrow viewport with Vietnamese text", async ({
  page,
}, testInfo) => {
  await mockApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  for (const path of ["/", "/movies/1", "/showtimes/7/seats"]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    const widths = await page.evaluate(() => [
      document.documentElement.scrollWidth,
      innerWidth,
    ]);
    expect(widths[0]).toBeLessThanOrEqual(widths[1]);
    await page.screenshot({
      path: testInfo.outputPath(
        `mobile-${path.replaceAll("/", "_") || "home"}.png`,
      ),
      fullPage: true,
    });
  }
});

test("profile is read-only and password change uses the OTP endpoints", async ({
  page,
}) => {
  const state = await mockApi(page);
  await page.goto("/account");
  await login(page);
  await expect(
    page.getByText("user@example.com", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Chỉnh sửa thông tin" }),
  ).toContainText("Sắp có");
  await page.getByRole("button", { name: "Đổi mật khẩu", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Mật khẩu hiện tại").fill("test-password");
  await dialog.getByRole("button", { name: "Gửi mã OTP" }).click();
  await dialog.getByLabel("Mã OTP").fill("123456");
  await dialog.getByLabel("Mật khẩu mới", { exact: true }).fill("new-password");
  await dialog.getByLabel("Nhập lại mật khẩu mới").fill("new-password");
  await dialog.getByRole("button", { name: "Xác nhận đổi mật khẩu" }).click();
  await expect(dialog.getByRole("status")).toContainText("Đã đổi mật khẩu");
  expect(
    state.writes.find((w) => w.path.endsWith("password/confirm"))?.body,
  ).toEqual({ otp: "123456", newPassword: "new-password" });
  expect(state.writes.some((w) => w.path === "/api/users/me")).toBe(false);
});

test("an in-flight hold cannot attach its result to a different signed-in user", async ({
  page,
}) => {
  const state = await mockApi(page);
  let release!: () => void;
  const paused = new Promise<void>((resolve) => {
    release = resolve;
  });
  let requested = false;
  await page.route(
    (url) => url.pathname === "/api/bookings",
    async (route) => {
      requested = true;
      await paused;
      await route.fulfill({
        status: 201,
        json: { status: 201, message: "OK", data: state.booking },
      });
    },
  );
  await page.goto("/showtimes/7/seats");
  await login(page);
  await page.getByRole("button", { name: /Ghế A1/ }).click();
  await page.getByRole("button", { name: "Giữ ghế & tiếp tục" }).click();
  await expect.poll(() => requested).toBe(true);
  await page.getByRole("button", { name: "Đăng xuất" }).click();
  state.userId = 8;
  await login(page);
  const response = page.waitForResponse(
    (value) => new URL(value.url()).pathname === "/api/bookings",
  );
  release();
  await response;
  await expect(
    page.getByRole("button", { name: "Giữ ghế & tiếp tục" }),
  ).toBeEnabled();
  await expect(page).toHaveURL(/\/showtimes\/7\/seats$/);
  await page
    .getByRole("navigation", { name: "Điều hướng chính" })
    .getByRole("link", { name: "Vé của tôi" })
    .click();
  await expect(page.getByText("Chưa có vé", { exact: true })).toBeVisible();
  await expect(page.getByText("BK-TEST-12", { exact: true })).not.toBeVisible();
});
