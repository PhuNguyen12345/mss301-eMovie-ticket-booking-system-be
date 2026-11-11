import { expect, test, type Page } from "@playwright/test";

const paged = (data: unknown[]) => ({
  data,
  page: 0,
  size: 10,
  totalElements: data.length,
  totalPages: 1,
  first: true,
  last: true,
});

/** Staff (user 1) looking at an order placed by customer 73. */
async function setup(page: Page, role: "ADMIN" | "CUSTOMER" = "ADMIN") {
  const state = {
    calls: [] as { path: string; query: string; body: unknown }[],
    usersStatus: 200,
    checkInClosed: false,
    booking: {
      id: 12,
      bookingCode: "BK261111113015WRTYLY",
      userId: 73,
      showtimeId: 9,
      status: "CONFIRMED",
      subtotal: 180000,
      discountAmount: 0,
      totalAmount: 180000,
      bookingTime: "2026-11-11T11:30:00",
      expiredAt: "2026-11-11T11:35:00",
      admittedCount: 0,
      ticket: {
        movieTitle: "Avatar: Lửa Và Tro Tàn",
        moviePosterUrl: null,
        startTime: "2026-11-11T13:25:00",
        endTime: "2026-11-11T16:42:00",
        cinemaName: "eMovie Sài Gòn – Quận 1",
        screenName: "Phòng chiếu 2",
      },
      seats: [
        {
          seatId: 101,
          rowCode: "F",
          seatNumber: 5,
          seatTypeName: "Ghế VIP",
          price: 105000,
          status: "BOOKED",
        },
        {
          seatId: 102,
          rowCode: "E",
          seatNumber: 5,
          seatTypeName: "Ghế thường",
          price: 75000,
          status: "BOOKED",
        },
      ],
      combos: [],
      vouchers: [],
      showtimeCancelledAt: null,
      payment: {
        id: 3,
        bookingId: 12,
        orderCode: 1201,
        provider: "PAYOS",
        amount: 180000,
        status: "SUCCESS",
        checkoutUrl: null,
        paidAt: "2026-11-11T11:31:00",
      },
    },
  };
  await page.clock.setFixedTime(new Date("2026-11-11T13:15:00+07:00"));
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname;
      const body = req.postData() ? req.postDataJSON() : null;
      state.calls.push({ path, query: url.search, body });
      const send = (data: unknown, status = 200, message = "OK") =>
        route.fulfill({ status, json: { status, message, data } });
      if (path === "/api/auth/refresh")
        return send({
          accessToken: "test-token",
          tokenType: "Bearer",
          expiresIn: 3600,
          refreshToken: null,
          refreshExpiresIn: 3600,
          user: {
            userId: role === "ADMIN" ? 1 : 73,
            fullName: role === "ADMIN" ? "Administrator" : "Nguyễn Văn An",
            roles: [role],
          },
        });
      if (path === "/api/users") {
        if (state.usersStatus !== 200)
          return send(null, state.usersStatus, "User service unavailable");
        return send([
          {
            userId: 73,
            fullName: "Nguyễn Văn An",
            email: "an.nguyen@example.com",
            phone: "0901234561",
            status: "ACTIVE",
          },
        ]);
      }
      if (path === "/api/bookings") return send(paged([state.booking]));
      if (path === "/api/bookings/12") return send(state.booking);
      if (path === "/api/bookings/12/qr")
        return route.fulfill({
          status: 200,
          contentType: "image/png",
          body: Buffer.from([137, 80, 78, 71]),
        });
      if (path === "/api/bookings/check-in" && state.checkInClosed)
        return route.fulfill({
          status: 409,
          json: {
            status: 409,
            code: "CHECK_IN_NOT_OPEN",
            message:
              "Check-in for this showtime is open from 2026-11-11T13:10 to 2026-11-11T13:45",
            data: {
              startTime: "2026-11-11T13:25:00",
              opensAt: "2026-11-11T13:10:00",
              closesAt: "2026-11-11T13:45:00",
            },
          },
        });
      if (path === "/api/bookings/check-in") {
        const quantity = Number((body as { quantity?: number })?.quantity);
        state.booking.admittedCount += quantity;
        return send({
          bookingCode: state.booking.bookingCode,
          status: "CONFIRMED",
          movieTitle: state.booking.ticket.movieTitle,
          cinemaName: state.booking.ticket.cinemaName,
          screenName: state.booking.ticket.screenName,
          startTime: state.booking.ticket.startTime,
          seats: state.booking.seats,
          seatCount: 2,
          admittedCount: state.booking.admittedCount,
          remaining: 2 - state.booking.admittedCount,
          checkIns: [
            { quantity, checkedInAt: "2026-11-11T13:15:00", checkedInBy: 1 },
          ],
        });
      }
      if (path === "/api/payments") return send(paged([]));
      return send(null, 404, "Not mocked: " + path);
    },
  );
  return state;
}

test("staff open an order from the list and see who placed it", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/orders");
  await expect(
    page.getByText("Nguyễn Văn An · an.nguyen@example.com"),
  ).toBeVisible();
  expect(state.calls.filter((c) => c.path === "/api/users")).toHaveLength(1);
  expect(state.calls.find((c) => c.path === "/api/users")?.query).toBe(
    "?ids=73",
  );
  await page.getByRole("link", { name: /Avatar: Lửa Và Tro Tàn/ }).click();
  await expect(page).toHaveURL(/\/admin\/orders\/12$/);
  await expect(
    page.getByRole("heading", { name: "Chi tiết đơn vé" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Đơn vé", exact: true }),
  ).toHaveClass(/active/);
  await expect(page.getByText("an.nguyen@example.com")).toBeVisible();
  await expect(page.getByText("0901234561")).toBeVisible();
  await expect(page.getByText("BK261111113015WRTYLY")).toBeVisible();
  await expect(page.getByRole("img", { name: /QR/ })).toHaveCount(0);
  await page
    .getByRole("link", { name: "Xem các đơn khác của khách →" })
    .click();
  await expect(page).toHaveURL(/\/admin\/orders\?userId=73$/);
  await expect(page.getByLabel("ID khách hàng")).toHaveValue("73");
  expect(
    state.calls.some(
      (c) => c.path === "/api/bookings" && c.query.includes("userId=73"),
    ),
  ).toBe(true);
});

test("staff admit part of a group and then the rest from the order page", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/orders/12");
  await expect(page.getByText(/Đã vào rạp:\s*0\s*\/ 2 khách/)).toBeVisible();
  await page.getByLabel("Số khách vào lần này (còn 2)").fill("1");
  await page.getByRole("button", { name: "Cho khách vào" }).click();
  await page.getByRole("button", { name: "Xác nhận soát vé" }).click();
  await expect(
    page.getByText("Đã ghi nhận 1 / 2 khách vào rạp."),
  ).toBeVisible();
  await expect(page.getByText(/Đã vào rạp:\s*1\s*\/ 2 khách/)).toBeVisible();
  expect(
    state.calls.find((c) => c.path === "/api/bookings/check-in")?.body,
  ).toEqual({
    bookingCode: "BK261111113015WRTYLY",
    quantity: 1,
  });
  await page.getByLabel("Số khách vào lần này (còn 1)").fill("1");
  await page.getByRole("button", { name: "Cho khách vào" }).click();
  await page.getByRole("button", { name: "Xác nhận soát vé" }).click();
  await expect(
    page.getByText("Tất cả khách của đơn này đã vào rạp."),
  ).toBeVisible();
});

test("the customer page sends staff to the admin view, and a failed lookup still shows the order", async ({
  page,
}) => {
  const state = await setup(page);
  state.usersStatus = 503;
  await page.goto("/bookings/12");
  await expect(page).toHaveURL(/\/admin\/orders\/12$/);
  await expect(
    page.getByText("Không tải được thông tin khách hàng.", { exact: false }),
  ).toBeVisible();
  await expect(page.getByText("BK261111113015WRTYLY")).toBeVisible();
});

test("customers keep their own ticket page and cannot open the admin order view", async ({
  page,
}) => {
  const state = await setup(page, "CUSTOMER");
  await page.goto("/bookings/12");
  await expect(page).toHaveURL(/\/bookings\/12$/);
  await expect(
    page.getByRole("heading", { name: "Chi tiết đặt vé" }),
  ).toBeVisible();
  await page.goto("/admin/orders/12");
  await expect(
    page.getByRole("heading", { name: "Chi tiết đơn vé" }),
  ).toHaveCount(0);
  expect(state.calls.some((c) => c.path === "/api/users")).toBe(false);
});

test("check-in outside the window is explained in Vietnamese with the opening hours", async ({
  page,
}) => {
  const state = await setup(page);
  state.checkInClosed = true;
  await page.clock.setFixedTime(new Date("2026-11-11T12:00:00+07:00"));
  await page.goto("/admin/orders/12");
  await page.getByRole("button", { name: "Cho khách vào" }).click();
  await page.getByRole("button", { name: "Xác nhận soát vé" }).click();
  await expect(
    page
      .getByRole("dialog")
      .getByText(
        "Chưa đến giờ soát vé. Suất 13:25 ngày 11/11/2026 soát vé từ 13:10 đến 13:45.",
      ),
  ).toBeVisible();
  await expect(page.getByText(/2026-11-11T13:10/)).toHaveCount(0);
});
