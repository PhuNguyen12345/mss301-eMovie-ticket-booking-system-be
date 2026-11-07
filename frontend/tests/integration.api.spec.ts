import { test, expect, type Page } from "@playwright/test";
import { cinemaResponse, cinemas } from "./cinema-fixtures";
const paged = (data: unknown[]) => ({
  data,
  page: 0,
  size: 10,
  totalElements: data.length,
  totalPages: data.length ? 1 : 0,
  first: true,
  last: true,
});
async function setup(page: Page) {
  const state = {
    cinemas: [...cinemas],
    writes: [] as { path: string; body: any }[],
    reads: [] as string[],
    cinemaStatus: 201,
    comboStatus: 200,
    verifyStatus: 400,
    booking: {
      id: 12,
      bookingCode: "BK-SNAPSHOT",
      userId: 42,
      showtimeId: 7,
      status: "PENDING_PAYMENT",
      totalAmount: 90000,
      bookingTime: "2030-01-01T08:00:00",
      expiredAt: "2030-01-01T08:05:00",
      ticket: {
        movieTitle: "Tên phim lúc mua",
        moviePosterUrl: null,
        startTime: "2030-01-01T18:00:00",
        endTime: "2030-01-01T20:00:00",
        cinemaName: "Rạp Hà Nội",
        screenName: "Phòng một",
      },
      seats: [
        {
          seatId: 101,
          rowCode: "A",
          seatNumber: 1,
          seatTypeName: "Standard",
          price: 90000,
          status: "HELD",
        },
      ],
      combos: [] as {
        comboId: number;
        name: string;
        quantity: number;
        price: number;
      }[],
      payment: null,
    },
  };
  await page.clock.setFixedTime(new Date("2030-01-01T08:00:00+07:00"));
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        path = url.pathname;
      const send = (data: unknown, status = 200, message = "OK") =>
        route.fulfill({ status, json: { status, data, message } });
      const body = request.postData() ? request.postDataJSON() : null;
      if (request.method() !== "GET") state.writes.push({ path, body });
      else state.reads.push(url.pathname + url.search);
      if (path === "/api/auth/login")
        return send({
          accessToken: "admin-test-token",
          tokenType: "Bearer",
          expiresIn: 3600,
          user: { userId: 42, fullName: "Admin", roles: ["ADMIN"] },
        });
      if (
        path === "/api/auth/register" ||
        path === "/api/auth/register/resend-otp"
      )
        return send(
          {
            registrationId: "8b4a1b86-d0df-4e06-af42-d32fb0b1e4de",
            expiresAt: "2030-01-01T01:05:00Z",
            resendAvailableAt: "2030-01-01T00:59:00Z",
            otpDeliveryStatus: "PENDING",
          },
          202,
        );
      if (path === "/api/auth/register/verify")
        return state.verifyStatus === 201
          ? send(
              { userId: 50, email: "new@example.test", status: "ACTIVE" },
              201,
            )
          : send(null, 400, "OTP không đúng");
      if (path === "/api/auth/password/forgot") return send(null, 202);
      if (path === "/api/auth/password/reset")
        return send({ changedAt: "2030-01-01T01:00:00Z" });
      if (path === "/api/cinemas") {
        if (request.method() === "POST") {
          if (state.cinemaStatus !== 201)
            return send(null, state.cinemaStatus, "Mã rạp đã tồn tại");
          const created = { id: 3, ...body, status: "ACTIVE" };
          state.cinemas.push(created);
          return send(created, 201);
        }
        return send(state.cinemas);
      }
      const catalogue = cinemaResponse(path);
      if (catalogue !== undefined) return send(catalogue);
      if (path === "/api/movies")
        return send(
          paged([
            {
              id: 1,
              title: "Interstellar",
              durationMinutes: 120,
              status: "PRESENTING",
            },
          ]),
        );
      if (path === "/api/showtimes")
        return request.method() === "POST"
          ? send(
              {
                id: 7,
                ...body,
                endTime: "2030-01-01T20:00:00",
                status: "SCHEDULED",
              },
              201,
            )
          : send(paged([]));
      if (path === "/api/bookings") return send(paged([state.booking]));
      if (path === "/api/bookings/12") return send(state.booking);
      if (path === "/api/bookings/me") return send(paged([state.booking]));
      if (path === "/api/combos" || path === "/api/combos/all")
        return send([
          {
            id: 1,
            name: "Bắp và nước",
            description: null,
            price: 45000,
            status: "ACTIVE",
          },
        ]);
      if (path === "/api/bookings/12/combos") {
        if (state.comboStatus !== 200)
          return send(null, state.comboStatus, "Combo không còn khả dụng");
        state.booking.combos = body.combos.map(
          (c: { comboId: number; quantity: number }) => ({
            ...c,
            name: "Bắp và nước",
            price: 47000,
          }),
        );
        state.booking.totalAmount =
          90000 +
          state.booking.combos.reduce(
            (sum, c) => sum + c.price * c.quantity,
            0,
          );
        return send(state.booking);
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
  await dialog.getByLabel("Email").fill("admin@example.test");
  await dialog.getByLabel("Mật khẩu", { exact: true }).fill("Password123");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(dialog).toBeHidden();
}
test("showtime selectors reset the room when cinema changes and submit real IDs", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/showtimes");
  await login(page);
  await page
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phim", { exact: true }).selectOption("1");
  await dialog.getByLabel("Rạp", { exact: true }).selectOption("1");
  await dialog.getByLabel("Phòng chiếu").selectOption("1");
  await dialog.getByLabel("Rạp", { exact: true }).selectOption("2");
  await expect(dialog.getByLabel("Phòng chiếu")).toHaveValue("");
  await dialog
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  expect(state.writes.filter((w) => w.path === "/api/showtimes")).toHaveLength(
    0,
  );
  await dialog.getByLabel("Phòng chiếu").selectOption("2");
  await dialog
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(dialog).toBeHidden();
  expect(
    state.writes.find((w) => w.path === "/api/showtimes")?.body,
  ).toMatchObject({ cinemaId: 2, screenId: 2, movieId: 1 });
});
test("cinema creation preserves failed input and refreshes catalogue after success", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/cinema");
  await login(page);
  await page.getByRole("button", { name: "Thêm rạp", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Mã", { exact: true }).fill("DANANG");
  await dialog.getByLabel("Tên", { exact: true }).fill("Rạp Đà Nẵng");
  await dialog.getByLabel("Địa điểm").fill("Đà Nẵng");
  state.cinemaStatus = 409;
  await dialog.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("Mã rạp đã tồn tại");
  expect(state.writes.filter((w) => w.path === "/api/cinemas")).toHaveLength(1);
  await expect(dialog.getByLabel("Tên", { exact: true })).toHaveValue(
    "Rạp Đà Nẵng",
  );
  state.cinemaStatus = 201;
  await dialog.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole("button", { name: "Rạp Đà Nẵng DANANG", exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
test("registration requires OTP verification, supports resend and does not store credentials", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/register");
  await page.getByLabel("Họ và tên").fill("Khách mới");
  await page.getByLabel("Email", { exact: true }).fill("new@example.test");
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill("NewPassword123");
  await page.getByLabel("Nhập lại mật khẩu").fill("NewPassword123");
  await page.getByRole("button", { name: "Gửi mã xác thực" }).click();
  await expect(page.getByLabel("Mã OTP")).toBeVisible();
  await expect(
    page.getByText("Tài khoản đã được xác thực.", { exact: false }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Gửi lại mã OTP", exact: true })
    .click();
  await expect
    .poll(
      () => state.writes.filter((w) => w.path.endsWith("resend-otp")).length,
    )
    .toBe(1);
  await page.getByLabel("Mã OTP").fill("111111");
  await page.getByRole("button", { name: "Xác thực tài khoản" }).click();
  await expect(page.getByRole("alert")).toContainText("OTP không đúng");
  state.verifyStatus = 201;
  await page.getByLabel("Mã OTP").fill("222222");
  await page.getByRole("button", { name: "Xác thực tài khoản" }).click();
  await expect(page.getByRole("status")).toContainText(
    "Tài khoản đã được xác thực",
  );
  expect(
    await page.evaluate(
      () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
    ),
  ).not.toContain("NewPassword123");
});
test("password reset keeps the generic account response and submits OTP with new password", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/forgot-password");
  await page.getByLabel("Email").fill("new@example.test");
  await page.getByRole("button", { name: "Gửi mã khôi phục" }).click();
  await expect(
    page.getByText("Nếu tài khoản tồn tại", { exact: false }),
  ).toBeVisible();
  await page.getByLabel("Mã OTP").fill("123456");
  await page
    .getByLabel("Mật khẩu mới", { exact: true })
    .fill("ResetPassword123");
  await page.getByLabel("Nhập lại mật khẩu").fill("ResetPassword123");
  await page
    .getByRole("button", { name: "Đặt lại mật khẩu", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText(
    "Mật khẩu đã được cập nhật",
  );
  expect(state.writes.find((w) => w.path.endsWith("/reset"))?.body).toEqual({
    email: "new@example.test",
    otp: "123456",
    newPassword: "ResetPassword123",
  });
});
test("ticket uses its snapshot and combo changes use backend prices without retrying conflicts", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/bookings/12");
  await login(page);
  await expect(
    page.getByRole("heading", { name: "Tên phim lúc mua" }),
  ).toBeVisible();
  expect(
    state.reads.some((path) => /\/showtimes\/7|\/movies\/1|\/seats/.test(path)),
  ).toBe(false);
  await page.getByRole("button", { name: "Chọn combo", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("spinbutton").fill("2");
  state.comboStatus = 409;
  await dialog.getByRole("button", { name: "Lưu combo vào vé" }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "Combo không còn khả dụng",
  );
  expect(
    state.writes.filter((w) => w.path.endsWith("/12/combos")),
  ).toHaveLength(1);
  state.comboStatus = 200;
  await dialog.getByRole("button", { name: "Lưu combo vào vé" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator(".summary-lines .total")).toContainText("184.000");
  await expect(page.getByText("Bắp và nước × 2")).toBeVisible();
});
test("admin search sends server-side filters and shows ticket details", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/orders");
  await login(page);
  await page.getByLabel("Mã đặt vé", { exact: true }).fill("BK-SNAPSHOT");
  await page.getByLabel("ID khách hàng").fill("42");
  await page.getByLabel("Trạng thái đơn").selectOption("PENDING_PAYMENT");
  await page.getByRole("button", { name: "Tìm đơn vé" }).click();
  await expect
    .poll(() =>
      state.reads.some(
        (path) =>
          path.includes("code=BK-SNAPSHOT") &&
          path.includes("userId=42") &&
          path.includes("status=PENDING_PAYMENT"),
      ),
    )
    .toBe(true);
  await expect(
    page.getByRole("heading", { name: "Tên phim lúc mua" }),
  ).toBeVisible();
});
