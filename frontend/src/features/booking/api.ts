import { z } from "zod";
import type { HttpClient } from "../../lib/http";
import { pageSchema, showtimeSchema } from "../showtimes/model/contracts";
const id = z.number().int().positive().safe();
const amount = z.number().finite().nonnegative();
export const paymentSchema = z.discriminatedUnion("status", [
  z.object({
    id,
    bookingId: id,
    orderCode: id,
    provider: z.string(),
    amount,
    status: z.enum(["PENDING", "SUCCESS", "FAILED", "REFUNDED"]),
    checkoutUrl: z.string().nullable(),
    paidAt: z.string().nullable(),
  }),
  z.object({
    id: z.null(),
    bookingId: id,
    orderCode: z.null(),
    provider: z.null(),
    amount: z.literal(0),
    status: z.literal("NOT_REQUIRED"),
    checkoutUrl: z.null(),
    paidAt: z.null(),
  }),
]);
export const bookingSchema = z.object({
  id,
  bookingCode: z.string(),
  userId: id,
  showtimeId: id,
  status: z.enum([
    "PENDING_PAYMENT",
    "CONFIRMED",
    "CANCELLED",
    "EXPIRED",
    "SHOWTIME_CANCELLED",
  ]),
  totalAmount: amount,
  subtotal: amount.optional(),
  discountAmount: amount.optional(),
  admittedCount: z.number().int().nonnegative().optional(),
  showtimeCancelledAt: z.string().nullish(),
  vouchers: z
    .array(
      z.object({
        voucherId: id,
        code: z.string(),
        kind: z.enum([
          "TICKET_EXCHANGE",
          "TICKET_DISCOUNT",
          "COMBO_EXCHANGE",
          "COMBO_DISCOUNT",
          "VALUE",
        ]),
        seatId: id.nullable(),
        comboId: id.nullable(),
        discountAmount: amount,
        expiresOn: z.string(),
      }),
    )
    .optional(),
  bookingTime: z.string(),
  expiredAt: z.string(),
  ticket: z
    .object({
      movieTitle: z.string(),
      moviePosterUrl: z.string().nullable(),
      startTime: z.string(),
      endTime: z.string(),
      cinemaName: z.string(),
      screenName: z.string(),
    })
    .nullish(),
  combos: z
    .array(
      z.object({
        comboId: id,
        name: z.string(),
        quantity: z.number().int().positive(),
        price: amount,
      }),
    )
    .optional(),
  seats: z.array(
    z.object({
      seatId: id,
      rowCode: z.string().nullish(),
      seatNumber: z.number().int().positive().nullish(),
      seatTypeName: z.string().nullish(),
      price: amount,
      status: z.enum(["HELD", "BOOKED", "RELEASED"]),
    }),
  ),
  payment: paymentSchema.nullable(),
});
export const seatMapSchema = z.object({
  showtimeId: id,
  screenId: id,
  seats: z.array(
    z.object({
      seatId: id,
      rowCode: z.string(),
      seatNumber: z.number().int().positive(),
      seatTypeId: id,
      price: amount,
      status: z.enum(["AVAILABLE", "HELD", "BOOKED", "UNAVAILABLE"]),
    }),
  ),
});
export const refundSchema = z.object({
  id,
  paymentId: id,
  bookingId: id,
  orderCode: id,
  amount,
  reason: z.string(),
  method: z.enum(["MANUAL", "VOUCHER"]).optional(),
  voucherId: id.nullish(),
  status: z.enum(["PENDING", "COMPLETED", "REJECTED"]),
  createdAt: z.string(),
  refundedAt: z.string().nullable(),
});
export type Booking = z.infer<typeof bookingSchema>;
export type SeatMap = z.infer<typeof seatMapSchema>;
export type Refund = z.infer<typeof refundSchema>;
export type BookingSearch = {
  code?: string;
  status?: string;
  userId?: number;
  showtimeId?: number;
  from?: string;
  to?: string;
  page: number;
  size: number;
};
export function createBookingApi(client: HttpClient) {
  return {
    qr: (id: number, signal?: AbortSignal) =>
      client.request(`/bookings/${id}/qr`, z.instanceof(Blob), {
        signal,
        responseType: "png",
      }),
    applyVoucher: (id: number, code: string, seatId?: number) =>
      client.request(`/bookings/${id}/vouchers`, bookingSchema, {
        method: "POST",
        body: { code, seatId },
      }),
    removeVoucher: (id: number, voucherId: number) =>
      client.request(`/bookings/${id}/vouchers/${voucherId}`, bookingSchema, {
        method: "DELETE",
      }),
    search: (filters: BookingSearch, signal?: AbortSignal) => {
      const params = new URLSearchParams();
      Object.entries(filters).forEach(([key, value]) => {
        if (value !== undefined && value !== "") params.set(key, String(value));
      });
      return client.request(`/bookings?${params}`, pageSchema(bookingSchema), {
        signal,
      });
    },
    replaceCombos: (
      id: number,
      combos: { comboId: number; quantity: number }[],
    ) =>
      client.request(`/bookings/${id}/combos`, bookingSchema, {
        method: "PUT",
        body: { combos },
      }),
    showtime: (id: number, signal?: AbortSignal) =>
      client.request(`/showtimes/${id}`, showtimeSchema, { signal }),
    seats: (id: number, signal?: AbortSignal) =>
      client.request(`/bookings/showtimes/${id}/seats`, seatMapSchema, {
        signal,
      }),
    hold: (showtimeId: number, seatIds: number[]) =>
      client.request("/bookings", bookingSchema, {
        method: "POST",
        body: { showtimeId, seatIds },
      }),
    mine: (page: number, signal?: AbortSignal) =>
      client.request(
        `/bookings/me?page=${page}&size=10`,
        pageSchema(bookingSchema),
        { signal },
      ),
    detail: (id: number, signal?: AbortSignal) =>
      client.request(`/bookings/${id}`, bookingSchema, { signal }),
    cancel: (id: number) =>
      client.request(`/bookings/${id}/cancel`, bookingSchema, {
        method: "POST",
      }),
    pay: (id: number) =>
      client.request(`/bookings/${id}/payments`, paymentSchema, {
        method: "POST",
      }),
    sync: (id: number) =>
      client.request(`/payments/bookings/${id}/sync`, bookingSchema, {
        method: "POST",
      }),
    refunds: (status: Refund["status"], signal?: AbortSignal) =>
      client.request(
        `/payments/refunds?status=${status}`,
        z.array(refundSchema),
        { signal },
      ),
    completeRefund: (id: number) =>
      client.request(`/payments/refunds/${id}/complete`, refundSchema, {
        method: "POST",
      }),
  };
}

// Only navigate to the payment provider; URL query parameters never confirm payment.
export function paymentUrl(value: string | null): string {
  if (!value) throw new Error("Chưa có liên kết thanh toán. Vui lòng thử lại.");
  const url = new URL(value);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    !(url.hostname === "payos.vn" || url.hostname.endsWith(".payos.vn"))
  ) {
    throw new Error("Liên kết thanh toán không hợp lệ.");
  }
  return url.href;
}

// Booking-service creates orderCode = bookingId * 100 + attempt (1..99).
// This identifies a booking only; ownership and payment state are verified by the API.
export function bookingIdFromOrderCode(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null;
  const code = Number(value);
  return Number.isSafeInteger(code) && code >= 101 && code % 100 > 0
    ? Math.floor(code / 100)
    : null;
}
