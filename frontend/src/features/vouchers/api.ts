import { z } from "zod";
import type { HttpClient } from "../../lib/http";
import { pageSchema } from "../showtimes/model/contracts";

export const kinds = {
  TICKET_EXCHANGE: "Đổi vé",
  TICKET_DISCOUNT: "Giảm giá vé",
  COMBO_EXCHANGE: "Đổi combo",
  COMBO_DISCOUNT: "Giảm giá combo",
  VALUE: "Voucher giá trị",
} as const;
export const availability = {
  AVAILABLE: "Có thể dùng",
  RESERVED: "Đang giữ cho đơn vé",
  USED: "Đã dùng",
  EXPIRED: "Hết hạn",
  INACTIVE: "Chưa hiệu lực",
} as const;
const id = z.number().int().positive();
const terms = z.object({
  id,
  code: z.string(),
  kind: z.enum([
    "TICKET_EXCHANGE",
    "TICKET_DISCOUNT",
    "COMBO_EXCHANGE",
    "COMBO_DISCOUNT",
    "VALUE",
  ]),
  discountType: z.enum(["PERCENT", "FIXED"]),
  discountValue: z.number().positive(),
  startDate: z.string(),
  endDate: z.string(),
  minOrderAmount: z.number().nonnegative().nullable(),
  seatTypeId: id.nullable(),
  comboId: id.nullable(),
});
export const voucherSchema = terms.extend({
  status: z.enum(["ACTIVE", "INACTIVE"]),
  ownerId: id.nullable(),
  claimStatus: z.enum(["AVAILABLE", "RESERVED", "USED"]).nullable(),
  sourcePaymentId: id.nullable(),
  sourceBookingId: id.nullable(),
  sourceReason: z.string().nullable(),
});
export const myVoucherSchema = terms.extend({
  status: z.enum(["AVAILABLE", "RESERVED", "USED", "EXPIRED", "INACTIVE"]),
});
export type Voucher = z.infer<typeof voucherSchema>;
export type MyVoucher = z.infer<typeof myVoucherSchema>;
export type VoucherInput = Omit<z.infer<typeof terms>, "id" | "kind"> & {
  kind: Exclude<Voucher["kind"], "VALUE">;
};
export function createVoucherApi(client: HttpClient) {
  return {
    list: (page: number, signal?: AbortSignal) =>
      client.request(
        `/vouchers?page=${page}&size=10`,
        pageSchema(voucherSchema),
        { signal },
      ),
    mine: (signal?: AbortSignal) =>
      client.request("/vouchers/me", z.array(myVoucherSchema), { signal }),
    claim: (code: string) =>
      client.request("/vouchers/claim", myVoucherSchema, {
        method: "POST",
        body: { code },
      }),
    save: (input: VoucherInput, id?: number) =>
      client.request(id ? `/vouchers/${id}` : "/vouchers", voucherSchema, {
        method: id ? "PUT" : "POST",
        body: input,
      }),
    status: (id: number, active: boolean) =>
      client.request(
        `/vouchers/${id}/${active ? "activate" : "deactivate"}`,
        voucherSchema,
        { method: "POST" },
      ),
    assign: (id: number, userId: number) =>
      client.request(`/vouchers/${id}/assign`, voucherSchema, {
        method: "POST",
        body: { userId },
      }),
  };
}
