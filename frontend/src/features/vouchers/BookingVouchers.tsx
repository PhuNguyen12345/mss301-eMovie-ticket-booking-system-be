import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { useVouchers } from "./hooks";
import { kinds } from "./api";
import type { Booking } from "../booking/api";
import type { useBookingActions } from "../booking/hooks";

export function BookingVouchers({
  booking,
  actions,
  disabled,
}: {
  booking: Booking;
  actions: ReturnType<typeof useBookingActions>;
  disabled: boolean;
}) {
  const query = useVouchers();
  const [code, setCode] = useState("");
  const [seatId, setSeatId] = useState("");
  const selected = query.data?.find(
    (v) => v.code.toUpperCase() === code.trim().toUpperCase(),
  );
  return (
    <section className="booking-vouchers">
      <h3>Mã ưu đãi</h3>
      <Link className="text-link" to="/vouchers">
        Xem / nhận voucher →
      </Link>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await actions.applyVoucher.mutateAsync({
              code: code.trim(),
              seatId: Number(seatId) || undefined,
            });
            setCode("");
            setSeatId("");
          } catch {
            /* Keep selection after rejection. */
          }
        }}
      >
        <fieldset disabled={disabled}>
          <Field label="Chọn voucher đã nhận">
            <select
              value={selected?.code ?? ""}
              onChange={(e) => {
                setCode(e.target.value);
                setSeatId("");
              }}
            >
              <option value="">Nhập mã hoặc chọn trong ví</option>
              {query.data
                ?.filter((v) => v.status === "AVAILABLE")
                .map((v) => (
                  <option key={v.id} value={v.code}>
                    {v.code} · {kinds[v.kind]}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Nhập mã voucher">
            <input
              value={code}
              onChange={(e) => {
                setCode(e.target.value);
                setSeatId("");
              }}
              required
              maxLength={50}
              autoComplete="off"
            />
          </Field>
          {(!selected || selected.kind === "TICKET_EXCHANGE") && (
            <Field label="Ghế đổi vé (chỉ dành cho mã đổi vé)">
              <select
                value={seatId}
                required={selected?.kind === "TICKET_EXCHANGE"}
                onChange={(e) => setSeatId(e.target.value)}
              >
                <option value="">Không chọn ghế</option>
                {booking.seats.map((s) => (
                  <option key={s.seatId} value={s.seatId}>
                    {s.rowCode && s.seatNumber
                      ? `${s.rowCode}${s.seatNumber}`
                      : `Ghế #${s.seatId}`}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Button type="submit" pending={actions.applyVoucher.isPending}>
            Áp dụng mã
          </Button>
        </fieldset>
      </form>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
      <ErrorMessage
        error={actions.applyVoucher.error || actions.removeVoucher.error}
      />
      {booking.vouchers?.map((v) => (
        <div className="voucher-applied" key={v.voucherId}>
          <span>{v.code}</span>
          <Button
            variant="ghost"
            disabled={disabled}
            onClick={() => actions.removeVoucher.mutate(v.voucherId)}
          >
            Bỏ mã {v.code}
          </Button>
        </div>
      ))}
      <p className="muted">
        Giảm giá và tổng thanh toán được xác nhận sau khi áp dụng mã.
      </p>
    </section>
  );
}
