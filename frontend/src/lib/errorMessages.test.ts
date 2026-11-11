import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createHttpClient, errorMessage } from "./http";
import { translateError } from "./errorMessages";
import { z } from "zod";

afterEach(() => vi.useRealTimers());

describe("Vietnamese text for booking errors", () => {
  const window = {
    startTime: "2026-11-11T13:25:00",
    opensAt: "2026-11-11T13:10:00",
    closesAt: "2026-11-11T13:45:00",
  };
  it("explains a check-in that is not open yet or already closed, in Vietnam time", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-11T12:00:00+07:00"));
    expect(translateError("CHECK_IN_NOT_OPEN", window)).toBe(
      "Chưa đến giờ soát vé. Suất 13:25 ngày 11/11/2026 soát vé từ 13:10 đến 13:45.",
    );
    vi.setSystemTime(new Date("2026-11-11T14:00:00+07:00"));
    expect(translateError("CHECK_IN_NOT_OPEN", window)).toBe(
      "Đã hết giờ soát vé. Suất 13:25 ngày 11/11/2026 chỉ soát vé đến 13:45.",
    );
    expect(translateError("CHECK_IN_NOT_OPEN", null)).toBe(
      "Hiện không trong giờ soát vé của suất chiếu này.",
    );
  });
  it("uses the details the server sends and falls back when they are missing", () => {
    expect(translateError("CHECK_IN_TOO_MANY", { remaining: 1 })).toBe(
      "Đơn này chỉ còn 1 khách chưa vào rạp.",
    );
    expect(
      translateError("CHECK_IN_NOT_PAID", { status: "SHOWTIME_CANCELLED" }),
    ).toContain("Suất chiếu của đơn này đã bị hủy");
    expect(
      translateError("VOUCHER_MIN_ORDER", { minOrderAmount: 150000 }),
    ).toBe("Voucher chỉ áp dụng cho đơn từ 150.000 đ.");
    expect(
      translateError("VOUCHER_NOT_APPLICABLE", {
        reason: "SEAT_TYPE_MISMATCH",
      }),
    ).toBe("Voucher không áp dụng cho loại ghế này.");
    expect(
      translateError("VOUCHER_NOT_APPLICABLE", { reason: "SOMETHING_NEW" }),
    ).toBe("Voucher không áp dụng được cho đơn này.");
    expect(translateError("SEAT_LIMIT", { max: "8" })).toBe(
      "Bạn đã chọn quá số ghế cho phép.",
    );
    expect(
      translateError("TICKET_ALREADY_USED", {
        seatCount: 2,
        checkIns: [
          { quantity: 2, checkedInAt: "2026-11-11T13:20:00", checkedInBy: 1 },
        ],
      }),
    ).toBe(
      "Vé này đã vào rạp đủ 2 khách. Lần soát gần nhất lúc 13:20 ngày 11/11/2026.",
    );
    expect(translateError("SOMETHING_NEW", {})).toBeUndefined();
  });

  it("reads the code from the error envelope and keeps server text for unknown codes", async () => {
    const reply = (status: number, body: object) =>
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      });
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        reply(409, {
          status: 409,
          code: "CHECK_IN_TOO_MANY",
          message: "Only 1 person can still be checked in with this code",
          data: { remaining: 1 },
        }),
      )
      .mockResolvedValueOnce(
        reply(400, { status: 400, code: "BRAND_NEW", message: "Server text" }),
      )
      .mockResolvedValueOnce(
        reply(400, { status: 400, message: "Tên đã tồn tại" }),
      )
      .mockResolvedValueOnce(
        reply(403, { status: 403, code: "CONFLICT", message: "Access denied" }),
      );
    const client = createHttpClient({ baseUrl: "/api", fetcher });
    const call = () =>
      client
        .request("/bookings/check-in", z.unknown(), {
          method: "POST",
          body: {},
        })
        .catch((e) => e);

    const coded = await call();
    expect(coded).toBeInstanceOf(ApiError);
    expect(coded).toMatchObject({
      status: 409,
      code: "CHECK_IN_TOO_MANY",
      data: { remaining: 1 },
    });
    expect(errorMessage(coded)).toBe("Đơn này chỉ còn 1 khách chưa vào rạp.");
    expect(errorMessage(await call())).toBe("Server text");
    expect(errorMessage(await call())).toBe("Tên đã tồn tại");
    expect(errorMessage(await call())).toBe(
      "Tài khoản không có quyền thực hiện thao tác này.",
    );
  });
});
