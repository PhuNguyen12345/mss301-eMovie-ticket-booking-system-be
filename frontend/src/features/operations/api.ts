import { z } from "zod";
import type { HttpClient } from "../../lib/http";
import { pageSchema } from "../showtimes/model/contracts";

const id = z.number().int().positive().safe();
const count = z.number().int().nonnegative();
const amount = z.number().finite().nonnegative();
export const paymentLabels = {
  PENDING: "Chờ thanh toán",
  SUCCESS: "Thành công",
  FAILED: "Đã đóng",
  REFUNDED: "Đã hoàn",
} as const;
export const figuresSchema = z.object({
  bookings: count,
  tickets: count,
  ticketAmount: amount,
  comboAmount: amount,
  discountAmount: amount,
  revenue: amount,
  refundedAmount: amount,
});
export const statisticsSchema = z.object({
  from: z.string(),
  to: z.string(),
  groupBy: z.enum(["DAY", "MONTH", "MOVIE", "CINEMA"]),
  total: figuresSchema,
  rows: z.array(
    z.object({
      key: z.string().nullable(),
      label: z.string().nullable(),
      figures: figuresSchema,
    }),
  ),
});
export const paymentHistorySchema = z.object({
  id,
  bookingId: id,
  bookingCode: z.string(),
  userId: id,
  orderCode: id,
  provider: z.string(),
  amount,
  status: z.enum(["PENDING", "SUCCESS", "FAILED", "REFUNDED"]),
  providerTransactionId: z.string().nullable(),
  paidAt: z.string().nullable(),
  createdAt: z.string(),
  refunds: z.array(
    z.object({
      id,
      amount,
      method: z.enum(["MANUAL", "VOUCHER"]),
      voucherId: id.nullable(),
      status: z.enum(["PENDING", "COMPLETED", "REJECTED"]),
      reason: z.string(),
      createdAt: z.string(),
      refundedAt: z.string().nullable(),
    }),
  ),
});
export const checkInSchema = z.object({
  bookingCode: z.string(),
  status: z.enum([
    "PENDING_PAYMENT",
    "CONFIRMED",
    "CANCELLED",
    "EXPIRED",
    "SHOWTIME_CANCELLED",
  ]),
  movieTitle: z.string().nullable(),
  cinemaName: z.string().nullable(),
  screenName: z.string().nullable(),
  startTime: z.string(),
  seats: z.array(
    z.object({
      seatId: id,
      rowCode: z.string().nullable(),
      seatNumber: id.nullable(),
      seatTypeName: z.string().nullable(),
    }),
  ),
  seatCount: count,
  admittedCount: count,
  remaining: count,
  checkIns: z.array(
    z.object({ quantity: count, checkedInAt: z.string(), checkedInBy: id }),
  ),
});
export const cancellationSchema = z.object({
  showtimeId: id,
  cancelledPending: count,
  cancelledPaid: count,
  failed: count,
});
export type PaymentSearch = {
  status?: keyof typeof paymentLabels;
  bookingId?: number;
  orderCode?: number;
  from?: string;
  to?: string;
  page: number;
  size: number;
};
export type StatisticsSearch = {
  from: string;
  to: string;
  groupBy: "day" | "month" | "movie" | "cinema";
};
export type CheckIn = z.infer<typeof checkInSchema>;
function search(input: object) {
  const params = new URLSearchParams();
  Object.entries(input).forEach(([k, v]) => {
    if (v !== undefined && v !== "") params.set(k, String(v));
  });
  return params.toString();
}
export function createOperationsApi(client: HttpClient) {
  return {
    payments: (filters: PaymentSearch, signal?: AbortSignal) =>
      client.request(
        `/payments?${search(filters)}`,
        pageSchema(paymentHistorySchema),
        { signal },
      ),
    statistics: (filters: StatisticsSearch, signal?: AbortSignal) =>
      client.request(
        `/bookings/statistics?${search(filters)}`,
        statisticsSchema,
        { signal },
      ),
    checkIn: (bookingCode: string, quantity?: number) =>
      client.request("/bookings/check-in", checkInSchema, {
        method: "POST",
        body: { bookingCode, quantity },
      }),
    cancellation: (showtimeId: number) =>
      client.request(
        `/bookings/showtimes/${showtimeId}/cancellation`,
        cancellationSchema,
        { method: "POST" },
      ),
  };
}
