import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { formatDate, formatMoney, formatTime } from "../../lib/date";
import { BookingStatus } from "../booking/BookingStatus";
import type { Booking } from "../booking/api";
import { useBooking } from "../booking/hooks";
import { useCheckIn } from "../operations/hooks";
import { useCustomers } from "./hooks";

/** Staff view of one order: who placed it, what was bought, payment and door check-in. */
export function AdminOrderPage() {
  const { mode } = useSession();
  const id = Number(useParams().bookingId);
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Chi tiết đơn vé"
          description="Mở chế độ API để xem đơn vé thật."
        />
      </main>
    );
  if (!Number.isSafeInteger(id) || id <= 0)
    return (
      <main className="page">
        <EmptyState title="Mã đơn vé không hợp lệ" />
      </main>
    );
  return <OrderDetail key={id} id={id} />;
}

function OrderDetail({ id }: { id: number }) {
  const query = useBooking(id);
  return (
    <main className="page">
      <Link className="text-link" to="/admin/orders">
        ← Tra cứu đơn vé
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN TRỊ / ĐƠN VÉ</p>
          <h1>Chi tiết đơn vé</h1>
        </div>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError && !query.data ? (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      ) : (
        query.data && <OrderContent booking={query.data} />
      )}
    </main>
  );
}

function seatLabel(seat: Booking["seats"][number]) {
  return seat.rowCode && seat.seatNumber
    ? `${seat.rowCode}${seat.seatNumber}`
    : `#${seat.seatId}`;
}

const PAYMENT_LABELS: Record<string, string> = {
  SUCCESS: "Đã nhận thanh toán",
  REFUNDED: "Đã hoàn bằng voucher",
  FAILED: "Giao dịch đã đóng",
  PENDING: "Chờ thanh toán",
  NOT_REQUIRED: "Không cần thanh toán (voucher trả toàn bộ)",
};

function OrderContent({ booking }: { booking: Booking }) {
  const customers = useCustomers([booking.userId]);
  const customer = customers.data?.get(booking.userId);
  return (
    <div className="checkout-grid">
      <section className="surface ticket-detail">
        <div className="ticket-heading">
          <BookingStatus status={booking.status} />
          <span className="muted">Đơn #{booking.id}</span>
        </div>
        <h2>
          {booking.ticket?.movieTitle ?? `Suất chiếu #${booking.showtimeId}`}
        </h2>
        {booking.ticket && (
          <p className="muted">
            {formatDate(booking.ticket.startTime)} ·{" "}
            {formatTime(booking.ticket.startTime)} · {booking.ticket.cinemaName}{" "}
            · {booking.ticket.screenName}
          </p>
        )}
        <div className="booking-reference">
          <span>Mã đặt vé</span>
          <strong>{booking.bookingCode}</strong>
        </div>
        <dl className="summary-lines">
          {booking.seats.map((seat) => (
            <div key={seat.seatId}>
              <dt>
                Ghế {seatLabel(seat)}
                {seat.seatTypeName ? ` · ${seat.seatTypeName}` : ""}
              </dt>
              <dd>{formatMoney(seat.price)}</dd>
            </div>
          ))}
          {booking.combos?.map((combo) => (
            <div key={combo.comboId}>
              <dt>
                {combo.name} × {combo.quantity}
              </dt>
              <dd>{formatMoney(combo.price * combo.quantity)}</dd>
            </div>
          ))}
          {booking.vouchers?.map((v) => (
            <div key={v.voucherId}>
              <dt>Mã {v.code}</dt>
              <dd>−{formatMoney(v.discountAmount)}</dd>
            </div>
          ))}
          <div className="total">
            <dt>Tổng tiền</dt>
            <dd>{formatMoney(booking.totalAmount)}</dd>
          </div>
        </dl>
        <p className="muted">
          Đặt lúc {formatDate(booking.bookingTime)}{" "}
          {formatTime(booking.bookingTime)}
        </p>
        {booking.payment ? (
          <p className="muted">
            {booking.payment.orderCode
              ? `Giao dịch #${booking.payment.orderCode} · `
              : ""}
            {PAYMENT_LABELS[booking.payment.status] ?? booking.payment.status}
            {booking.payment.paidAt
              ? ` · ${formatDate(booking.payment.paidAt)} ${formatTime(booking.payment.paidAt)}`
              : ""}{" "}
            ·{" "}
            <Link to={`/admin/payments?bookingId=${booking.id}`}>
              Xem giao dịch
            </Link>
          </p>
        ) : (
          <p className="muted">Chưa có giao dịch.</p>
        )}
      </section>
      <aside className="surface order-summary">
        <h2>Khách hàng</h2>
        {customers.isPending ? (
          <Loading label="Đang tải thông tin khách…" />
        ) : customer ? (
          <dl className="summary-lines">
            <div>
              <dt>Họ tên</dt>
              <dd>{customer.fullName}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{customer.email}</dd>
            </div>
            <div>
              <dt>Điện thoại</dt>
              <dd>{customer.phone ?? "—"}</dd>
            </div>
          </dl>
        ) : (
          <p className="muted">
            {customers.isError
              ? "Không tải được thông tin khách hàng."
              : "Không tìm thấy tài khoản khách hàng."}{" "}
            Mã khách #{booking.userId}.
          </p>
        )}
        <Link
          className="text-link"
          to={`/admin/orders?userId=${booking.userId}`}
        >
          Xem các đơn khác của khách →
        </Link>
        <h2>Soát vé</h2>
        <DoorCheckIn booking={booking} />
      </aside>
    </div>
  );
}

function DoorCheckIn({ booking }: { booking: Booking }) {
  const mutation = useCheckIn();
  const total = booking.seats.length;
  const admitted = booking.admittedCount ?? 0;
  const remaining = Math.max(total - admitted, 0);
  const [quantity, setQuantity] = useState(String(remaining || 1));
  const [confirm, setConfirm] = useState(false);
  if (booking.status !== "CONFIRMED")
    return (
      <p className="muted">
        Chỉ soát vé cho đơn đã thanh toán. Đơn này đang ở trạng thái khác.
      </p>
    );
  return (
    <>
      <p>
        Đã vào rạp: <strong>{admitted}</strong> / {total} khách
      </p>
      {mutation.data && (
        <p className="notice" role="status">
          Đã ghi nhận {mutation.data.admittedCount} / {mutation.data.seatCount}{" "}
          khách vào rạp.
        </p>
      )}
      {remaining === 0 ? (
        <p className="muted">Tất cả khách của đơn này đã vào rạp.</p>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            mutation.reset();
            setConfirm(true);
          }}
        >
          <Field label={`Số khách vào lần này (còn ${remaining})`}>
            <input
              value={quantity}
              onChange={(e) => {
                setQuantity(e.target.value);
                mutation.reset();
              }}
              type="number"
              min={1}
              max={remaining}
              step={1}
              required
            />
          </Field>
          <Button type="submit" variant="primary">
            Cho khách vào
          </Button>
        </form>
      )}
      {!confirm && <ErrorMessage error={mutation.error} />}
      {confirm && (
        <Dialog
          title="Xác nhận cho khách vào rạp?"
          onClose={() => setConfirm(false)}
          busy={mutation.isPending}
        >
          <p className="dialog-description">
            Ghi nhận {quantity} khách cho mã {booking.bookingCode}. Chỉ cho
            khách vào sau khi hệ thống xác nhận thành công.
          </p>
          <ErrorMessage error={mutation.error} />
          <div className="dialog-actions">
            <Button
              disabled={mutation.isPending}
              onClick={() => setConfirm(false)}
            >
              Quay lại
            </Button>
            <Button
              variant="primary"
              pending={mutation.isPending}
              onClick={async () => {
                try {
                  await mutation.mutateAsync({
                    bookingCode: booking.bookingCode,
                    quantity: Number(quantity),
                  });
                  setConfirm(false);
                } catch {
                  /* Do not retry a check-in automatically. */
                }
              }}
            >
              Xác nhận soát vé
            </Button>
          </div>
        </Dialog>
      )}
    </>
  );
}
