import { describe, expect, it, vi } from "vitest";
import { createHttpClient } from "../../lib/http";
import { createBookingApi, paymentSchema, bookingSchema } from "../booking/api";
import { createOperationsApi } from "./api";

describe("new backend contracts", () => {
  it("accepts a zero-total confirmation without a payment row and rejects malformed payment successes", () => {
    const free = {
      id: null,
      bookingId: 12,
      orderCode: null,
      provider: null,
      amount: 0,
      status: "NOT_REQUIRED",
      checkoutUrl: null,
      paidAt: null,
    };
    expect(paymentSchema.parse(free).status).toBe("NOT_REQUIRED");
    expect(paymentSchema.safeParse({ ...free, amount: 1 }).success).toBe(false);
    expect(
      paymentSchema.safeParse({ ...free, status: "SUCCESS" }).success,
    ).toBe(false);
  });
  it("preserves cancelled-showtime and voucher fields in booking responses", () => {
    const result = bookingSchema.parse({
      id: 1,
      bookingCode: "BK1",
      userId: 1,
      showtimeId: 1,
      status: "SHOWTIME_CANCELLED",
      subtotal: 100000,
      discountAmount: 10000,
      totalAmount: 90000,
      bookingTime: "2026-11-09T08:00:00",
      expiredAt: "2026-11-09T08:05:00",
      showtimeCancelledAt: "2026-11-09T08:01:00",
      admittedCount: 0,
      vouchers: [
        {
          voucherId: 2,
          code: "TEST",
          kind: "VALUE",
          seatId: null,
          comboId: null,
          discountAmount: 10000,
          expiresOn: "2026-12-09",
        },
      ],
      seats: [],
      payment: null,
    });
    expect(result.vouchers?.[0].kind).toBe("VALUE");
    expect(result.showtimeCancelledAt).toBeTruthy();
  });
  it("fetches QR with authorization and accepts only nonempty PNG responses", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(new Uint8Array([137, 80, 78, 71]), {
          headers: { "Content-Type": "image/png" },
        }),
      )
      .mockResolvedValueOnce(
        new Response("html", { headers: { "Content-Type": "text/html" } }),
      );
    const api = createBookingApi(
      createHttpClient({
        baseUrl: "/api",
        accessToken: () => "private-token",
        fetcher,
      }),
    );
    expect((await api.qr(12)).type).toBe("image/png");
    expect(fetcher.mock.calls[0][1]?.headers).toMatchObject({
      Authorization: "Bearer private-token",
      Accept: "image/png",
    });
    await expect(api.qr(12)).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
  it("does not retry rejected check-ins and leaves filters on the server", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 409,
          message: "Already checked in",
          data: null,
        }),
        { status: 409 },
      ),
    );
    const api = createOperationsApi(
      createHttpClient({ baseUrl: "/api", fetcher }),
    );
    await expect(api.checkIn("BK-CODE", 2)).rejects.toMatchObject({
      status: 409,
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({
      bookingCode: "BK-CODE",
      quantity: 2,
    });
  });
});
