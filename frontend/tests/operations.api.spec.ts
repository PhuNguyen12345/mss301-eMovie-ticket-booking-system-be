import { expect, test, type Page } from "@playwright/test";
import { cinemaResponse } from "./cinema-fixtures";

const voucher = {
  id: 5,
  code: "MOVIE100",
  kind: "TICKET_DISCOUNT",
  discountType: "PERCENT",
  discountValue: 100,
  startDate: "2026-11-01",
  endDate: "2026-12-31",
  minOrderAmount: null,
  seatTypeId: null,
  comboId: null,
  status: "ACTIVE",
  ownerId: null as number | null,
  claimStatus: null,
  sourcePaymentId: null,
  sourceBookingId: null,
  sourceReason: null,
};
const figures = {
  bookings: 20,
  tickets: 40,
  ticketAmount: 4000000,
  comboAmount: 1000000,
  discountAmount: 500000,
  revenue: 4500000,
  refundedAmount: 100000,
};
const paged = (data: unknown[], page = 0, pages = 1) => ({
  data,
  page,
  size: 10,
  totalElements: pages * data.length,
  totalPages: pages,
  first: page === 0,
  last: page + 1 >= pages,
});
const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=",
  "base64",
);
async function setup(page: Page, admin = true) {
  const state = {
    calls: [] as {
      path: string;
      method: string;
      body: Record<string, unknown> | null;
      query: string;
    }[],
    applyStatus: 200,
    checkStatus: 200,
    claimStatus: 200,
    saveStatus: 201,
    qrStatus: 200,
    statisticsStatus: 200,
    vouchers: [{ ...voucher }],
    mine: [{ ...voucher, status: "AVAILABLE" }],
    booking: {
      id: 12,
      bookingCode: "BK-TEST-12",
      userId: 7,
      showtimeId: 9,
      status: "PENDING_PAYMENT",
      subtotal: 100000,
      discountAmount: 0,
      totalAmount: 100000,
      bookingTime: "2026-11-09T18:00:00",
      expiredAt: "2026-11-09T18:05:00",
      ticket: {
        movieTitle: "Interstellar",
        moviePosterUrl: null,
        startTime: "2026-11-09T19:00:00",
        endTime: "2026-11-09T21:00:00",
        cinemaName: "Rạp Hà Nội",
        screenName: "Phòng 1",
      },
      seats: [
        {
          seatId: 101,
          rowCode: "A",
          seatNumber: 1,
          seatTypeName: "Standard",
          price: 100000,
          status: "HELD",
        },
      ],
      combos: [],
      vouchers: [] as unknown[],
      admittedCount: 0,
      showtimeCancelledAt: null as string | null,
      payment: null,
    },
  };
  await page.clock.setFixedTime(new Date("2026-11-09T18:00:00+07:00"));
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname;
      const body = req.postData()
        ? (req.postDataJSON() as Record<string, unknown>)
        : null;
      state.calls.push({ path, method: req.method(), body, query: url.search });
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
            userId: 7,
            fullName: "Người kiểm thử",
            roles: [admin ? "ADMIN" : "CUSTOMER"],
          },
        });
      if (path === "/api/auth/logout") return send(null);
      expect(req.headers().authorization).toBe("Bearer test-token");
      const cinema = cinemaResponse(path);
      if (cinema !== undefined) return send(cinema);
      if (path === "/api/combos" || path === "/api/combos/all")
        return send([
          {
            id: 1,
            name: "Bắp nước",
            description: null,
            price: 50000,
            status: "ACTIVE",
          },
        ]);
      if (path === "/api/vouchers/me") return send(state.mine);
      if (path === "/api/vouchers/claim") {
        if (state.claimStatus !== 200)
          return send(null, state.claimStatus, "Mã không thể nhận");
        return send({
          ...voucher,
          code: String(body?.code),
          status: "AVAILABLE",
        });
      }
      if (path === "/api/vouchers" && req.method() === "GET")
        return send(
          paged(state.vouchers, Number(url.searchParams.get("page")), 2),
        );
      if (
        (path === "/api/vouchers" && req.method() === "POST") ||
        (path === "/api/vouchers/5" && req.method() === "PUT")
      ) {
        if (state.saveStatus === 409) return send(null, 409, "Mã đã tồn tại");
        state.vouchers = [{ ...voucher, ...body }];
        return send(state.vouchers[0], req.method() === "POST" ? 201 : 200);
      }
      if (path === "/api/vouchers/5/assign") {
        state.vouchers[0].ownerId = Number(body?.userId);
        return send(state.vouchers[0]);
      }
      if (path === "/api/vouchers/5/deactivate") {
        state.vouchers[0].status = "INACTIVE";
        return send(state.vouchers[0]);
      }
      if (path === "/api/vouchers/5/activate") {
        state.vouchers[0].status = "ACTIVE";
        return send(state.vouchers[0]);
      }
      if (path === "/api/bookings/12/vouchers") {
        if (state.applyStatus !== 200)
          return send(null, state.applyStatus, "Voucher không áp dụng được");
        state.booking.discountAmount = 100000;
        state.booking.totalAmount = 0;
        state.booking.vouchers = [
          {
            voucherId: 5,
            code: "MOVIE100",
            kind: "TICKET_DISCOUNT",
            seatId: null,
            comboId: null,
            discountAmount: 100000,
            expiresOn: "2026-12-31",
          },
        ];
        return send(state.booking);
      }
      if (path === "/api/bookings/12/vouchers/5") {
        state.booking.vouchers = [];
        state.booking.discountAmount = 0;
        state.booking.totalAmount = 100000;
        return send(state.booking);
      }
      if (path === "/api/bookings/12/payments") {
        state.booking.status = "CONFIRMED";
        state.mine[0].status = "USED";
        state.booking.seats[0].status = "BOOKED";
        return send({
          id: null,
          bookingId: 12,
          orderCode: null,
          provider: null,
          amount: 0,
          status: "NOT_REQUIRED",
          checkoutUrl: null,
          paidAt: null,
        });
      }
      if (path === "/api/bookings/12") return send(state.booking);
      if (path === "/api/bookings/me") return send(paged([state.booking]));
      if (path === "/api/bookings/12/qr") {
        if (state.qrStatus !== 200)
          return send(null, state.qrStatus, "Không thể tải QR");
        expect(req.headers().accept).toBe("image/png");
        return route.fulfill({
          status: 200,
          contentType: "image/png",
          body: png,
        });
      }
      if (path === "/api/bookings/check-in") {
        if (state.checkStatus !== 200)
          return send(null, state.checkStatus, "Vé đã được soát đủ số khách");
        const quantity = Number(body?.quantity);
        return send({
          bookingCode: body?.bookingCode,
          status: "CONFIRMED",
          movieTitle: "Interstellar",
          cinemaName: "Rạp Hà Nội",
          screenName: "Phòng 1",
          startTime: "2026-11-09T19:00:00",
          seats: state.booking.seats,
          seatCount: 3,
          admittedCount: quantity,
          remaining: 3 - quantity,
          checkIns: [
            { quantity, checkedInAt: "2026-11-09T18:50:00", checkedInBy: 7 },
          ],
        });
      }
      if (path === "/api/bookings/statistics") {
        if (state.statisticsStatus !== 200)
          return send(null, state.statisticsStatus, "Statistics unavailable");
        return send({
          from: url.searchParams.get("from"),
          to: url.searchParams.get("to"),
          groupBy: url.searchParams.get("groupBy")?.toUpperCase(),
          total: figures,
          rows: [{ key: "1", label: "Interstellar", figures }],
        });
      }
      if (path === "/api/payments")
        return send(
          paged(
            [
              {
                id: 1,
                bookingId: 12,
                bookingCode: "BK-TEST-12",
                userId: 7,
                orderCode: 1201,
                provider: "PAYOS",
                amount: 100000,
                status: "REFUNDED",
                providerTransactionId: "BANK-REF",
                paidAt: "2026-11-09T18:00:00",
                createdAt: "2026-11-09T18:00:00",
                refunds: [
                  {
                    id: 1,
                    amount: 100000,
                    method: "VOUCHER",
                    voucherId: 5,
                    status: "COMPLETED",
                    reason: "Suất chiếu bị hủy",
                    createdAt: "2026-11-09T18:10:00",
                    refundedAt: "2026-11-09T18:10:00",
                  },
                ],
              },
            ],
            Number(url.searchParams.get("page")),
            2,
          ),
        );
      if (path === "/api/bookings/showtimes/9/cancellation")
        return send({
          showtimeId: 9,
          cancelledPending: 1,
          cancelledPaid: 2,
          failed: 1,
        });
      return send(null, 404, `Missing fixture: ${path}`);
    },
  );
  return state;
}

test("applies/removes vouchers and confirms a zero-total ticket without PayOS navigation", async ({
  page,
}) => {
  const state = await setup(page, false);
  await page.goto("/bookings/12");
  await page.getByLabel("Chọn voucher đã nhận").selectOption("MOVIE100");
  await page.getByRole("button", { name: "Áp dụng mã" }).click();
  await expect(
    page.getByRole("button", { name: "Xác nhận vé bằng voucher" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Bỏ mã MOVIE100" }).click();
  await expect(
    page.getByRole("button", { name: "Thanh toán qua PayOS" }),
  ).toBeVisible();
  await page.getByLabel("Nhập mã voucher").fill("MOVIE100");
  await page.getByRole("button", { name: "Áp dụng mã" }).click();
  await page.getByRole("button", { name: "Xác nhận vé bằng voucher" }).click();
  await expect(page.getByText("Đã thanh toán", { exact: true })).toBeVisible();
  const image = page.getByRole("img", { name: "Mã QR vé do hệ thống cấp" });
  await expect(image).toHaveAttribute("src", /^blob:/);
  await expect(page).toHaveURL(/\/bookings\/12$/);
  await page.getByRole("link", { name: "Ưu đãi", exact: true }).click();
  await expect(page.getByText("Đã dùng", { exact: true })).toBeVisible();
  expect(
    state.calls.filter(
      (c) => c.path.endsWith("/payments") && c.method === "POST",
    ),
  ).toHaveLength(1);
  expect(
    state.calls.find((c) => c.path.endsWith("/vouchers") && c.method === "POST")
      ?.body,
  ).toEqual({ code: "MOVIE100" });
});

test("rejected vouchers retain the input and server total without retry", async ({
  page,
}) => {
  const state = await setup(page, false);
  state.applyStatus = 409;
  await page.goto("/bookings/12");
  await page.getByLabel("Nhập mã voucher").fill("WRONG");
  await page.getByRole("button", { name: "Áp dụng mã" }).click();
  await expect(page.getByText("Voucher không áp dụng được")).toBeVisible();
  await expect(page.getByLabel("Nhập mã voucher")).toHaveValue("WRONG");
  await expect(
    page.getByRole("button", { name: "Thanh toán qua PayOS" }),
  ).toBeVisible();
  expect(
    state.calls.filter((c) => c.path === "/api/bookings/12/vouchers"),
  ).toHaveLength(1);
});

test("ticket exchange sends the explicitly selected seat and QR failures stay visible", async ({
  page,
}) => {
  const state = await setup(page, false);
  state.mine[0].kind = "TICKET_EXCHANGE";
  await page.goto("/bookings/12");
  await page.getByLabel("Chọn voucher đã nhận").selectOption("MOVIE100");
  await page
    .getByLabel("Ghế đổi vé (chỉ dành cho mã đổi vé)")
    .selectOption("101");
  await page.getByRole("button", { name: "Áp dụng mã" }).click();
  await expect(
    page.getByRole("button", { name: "Xác nhận vé bằng voucher" }),
  ).toBeVisible();
  expect(
    state.calls.find((c) => c.path === "/api/bookings/12/vouchers")?.body,
  ).toEqual({ code: "MOVIE100", seatId: 101 });
  state.qrStatus = 409;
  await page.getByRole("button", { name: "Xác nhận vé bằng voucher" }).click();
  await expect(page.getByText("Không thể tải QR")).toBeVisible();
  await expect(
    page.getByRole("img", { name: "Mã QR vé do hệ thống cấp" }),
  ).not.toBeVisible();
});

test("cancelled paid bookings show compensation and never fetch a QR", async ({
  page,
}) => {
  const state = await setup(page, false);
  state.booking.status = "SHOWTIME_CANCELLED";
  state.booking.showtimeCancelledAt = "2026-11-09T18:01:00";
  await page.goto("/bookings/12");
  await expect(
    page.getByText("Suất chiếu đã hủy", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "ví ưu đãi" })).toBeVisible();
  expect(state.calls.some((c) => c.path.endsWith("/qr"))).toBe(false);
});

test("wallet distinguishes value vouchers and only acknowledges a successful claim", async ({
  page,
}) => {
  const state = await setup(page, false);
  state.mine[0] = {
    ...state.mine[0],
    kind: "VALUE",
    discountType: "FIXED",
    discountValue: 100000,
  };
  state.claimStatus = 404;
  await page.goto("/vouchers");
  await expect(page.getByText(/Voucher giá trị/)).toBeVisible();
  await page.getByLabel("Mã ưu đãi", { exact: true }).fill("NEWCODE");
  await page.getByRole("button", { name: "Nhận mã" }).click();
  await expect(page.getByText("Mã không thể nhận")).toBeVisible();
  await expect(page.getByLabel("Mã ưu đãi", { exact: true })).toHaveValue(
    "NEWCODE",
  );
  state.claimStatus = 200;
  await page.getByRole("button", { name: "Nhận mã" }).click();
  await expect(page.getByText("Đã nhận mã NEWCODE.")).toBeVisible();
});

test("voucher admin keeps rejected drafts and supports assignment, state and pagination", async ({
  page,
}) => {
  const state = await setup(page);
  state.saveStatus = 409;
  await page.goto("/admin/vouchers");
  await page.getByRole("button", { name: "Tạo voucher", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Mã (bỏ trống để tự tạo)").fill("NEWCODE");
  await dialog.getByRole("button", { name: "Lưu voucher" }).click();
  await expect(dialog.getByText("Mã đã tồn tại")).toBeVisible();
  await expect(dialog.getByLabel("Mã (bỏ trống để tự tạo)")).toHaveValue(
    "NEWCODE",
  );
  state.saveStatus = 201;
  await dialog.getByRole("button", { name: "Lưu voucher" }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole("button", { name: "Gán khách", exact: true }).click();
  await dialog.getByLabel("ID khách hàng").fill("9");
  await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(page.getByText("Khách #9", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tắt mã", exact: true }).click();
  await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Kích hoạt", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Trang sau" }).click();
  await expect
    .poll(() =>
      state.calls.some(
        (c) => c.path === "/api/vouchers" && c.query.includes("page=1"),
      ),
    )
    .toBe(true);
});

test("check-in requires confirmation, sends quantity once and rejects replay", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/check-in");
  await page.getByLabel("Mã đặt vé cần soát").fill("BK-TEST-12");
  await page.getByLabel("Số khách vào lần này").fill("2");
  await page.getByRole("button", { name: "Kiểm tra và cho vào" }).click();
  expect(state.calls.some((c) => c.path === "/api/bookings/check-in")).toBe(
    false,
  );
  await page.getByRole("button", { name: "Xác nhận soát vé" }).click();
  await expect(page.getByText("Đã ghi nhận khách vào rạp")).toBeVisible();
  expect(
    state.calls.find((c) => c.path === "/api/bookings/check-in")?.body,
  ).toEqual({ bookingCode: "BK-TEST-12", quantity: 2 });
  state.checkStatus = 409;
  await page.getByRole("button", { name: "Kiểm tra và cho vào" }).click();
  await page.getByRole("button", { name: "Xác nhận soát vé" }).click();
  await expect(page.getByText("Vé đã được soát đủ số khách")).toBeVisible();
  await expect(page.getByText("Đã ghi nhận khách vào rạp")).not.toBeVisible();
  expect(
    state.calls.filter((c) => c.path === "/api/bookings/check-in"),
  ).toHaveLength(2);
});

test("reports use server totals and filter by showtime dates on mobile", async ({
  page,
}) => {
  const state = await setup(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/admin/reports");
  await expect(
    page.locator(".metrics-grid").getByText("4.500.000 đ", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Từ ngày chiếu").fill("2026-11-01");
  await page.getByLabel("Đến ngày chiếu").fill("2026-11-09");
  await page.getByLabel("Nhóm theo").selectOption("movie");
  await page.getByRole("button", { name: "Xem thống kê" }).click();
  await expect
    .poll(() =>
      state.calls.some(
        (c) =>
          c.path.endsWith("statistics") &&
          c.query.includes("groupBy=movie") &&
          c.query.includes("from=2026-11-01"),
      ),
    )
    .toBe(true);
  await expect(page.getByText("Chi tiết theo phim")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "/tmp/emovie-fe-reports-mobile.png",
    fullPage: true,
  });
});

test("payment history paginates on server and explains voucher refunds", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/payments");
  await expect(page.getByText(/Hoàn bằng voucher #5/)).toBeVisible();
  await page.getByLabel("Trạng thái giao dịch").selectOption("REFUNDED");
  await page.getByLabel("ID đơn vé").fill("12");
  await page.getByRole("button", { name: "Tìm giao dịch" }).click();
  await expect
    .poll(() =>
      state.calls.some(
        (c) =>
          c.path === "/api/payments" &&
          c.query.includes("status=REFUNDED") &&
          c.query.includes("bookingId=12"),
      ),
    )
    .toBe(true);
  await page.getByRole("button", { name: "Trang sau" }).click();
  await expect
    .poll(() =>
      state.calls.some(
        (c) => c.path === "/api/payments" && c.query.includes("page=1"),
      ),
    )
    .toBe(true);
  await page.screenshot({
    path: "/tmp/emovie-fe-payments.png",
    fullPage: true,
  });
});

test("cancellation recovery is explicit and reports partial failures", async ({
  page,
}) => {
  const state = await setup(page);
  await page.goto("/admin/cancellations?showtimeId=9");
  await page.getByRole("button", { name: "Xử lý lại đơn vé" }).click();
  expect(state.calls.some((c) => c.path.endsWith("/cancellation"))).toBe(false);
  await page.getByRole("button", { name: "Xác nhận xử lý" }).click();
  await expect(page.getByText(/1 đơn xử lý lỗi/)).toBeVisible();
  expect(
    state.calls.filter((c) => c.path.endsWith("/cancellation")),
  ).toHaveLength(1);
});

test("customers cannot access new admin APIs", async ({ page }) => {
  const state = await setup(page, false);
  for (const path of [
    "vouchers",
    "reports",
    "payments",
    "check-in",
    "cancellations",
  ]) {
    await page.goto(`/admin/${path}`);
    await expect(
      page.getByRole("heading", { name: "Không có quyền truy cập" }),
    ).toBeVisible();
  }
  expect(
    state.calls.filter((c) => !c.path.startsWith("/api/auth/")),
  ).toHaveLength(0);
});
