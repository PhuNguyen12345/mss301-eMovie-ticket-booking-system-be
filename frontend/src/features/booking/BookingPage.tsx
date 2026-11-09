import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AccessGate } from "../../components/layout/AccessGate";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { TicketQr } from "./TicketQr";
import { BookingVouchers } from "../vouchers/BookingVouchers";
import { BookingCombos } from "../combos/BookingCombos";
import { VenueName } from "../cinema/VenueName";
import {
  useBooking,
  useBookingActions,
  useBookingShowtime,
  useSeatMap,
} from "./hooks";
import { useMovie } from "../movies/hooks";
import { BookingStatus } from "./BookingStatus";
import { type Booking, paymentUrl } from "./api";
import { formatDate, formatMoney, formatTime } from "../../lib/date";
import { useCountdown } from "../../lib/useCountdown";
import { useServices, useSession } from "../../app/ServicesProvider";
export function BookingPage() {
  const id = Number(useParams().bookingId);
  return (
    <AccessGate>
      <BookingDetails key={id} id={id} />
    </AccessGate>
  );
}
export function BookingDetails({
  id,
  fromPayment = false,
}: {
  id: number;
  fromPayment?: boolean;
}) {
  const query = useBooking(id);
  const actions = useBookingActions(id);
  const synced = useRef(false);
  useEffect(() => {
    if (fromPayment && query.data && !synced.current) {
      synced.current = true;
      actions.sync.mutate();
    }
  }, [fromPayment, query.data, actions.sync]);
  if (!Number.isSafeInteger(id) || id <= 0)
    return (
      <main className="page">
        <EmptyState title="Mã đặt vé không hợp lệ" />
      </main>
    );
  return (
    <main className="page cinema-page">
      <Link className="text-link" to="/bookings">
        ← Vé của tôi
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">
            {fromPayment ? "KẾT QUẢ THANH TOÁN" : "02 / ĐẶT VÉ & THANH TOÁN"}
          </p>
          <h1>Chi tiết đặt vé</h1>
        </div>
      </div>
      {fromPayment && (
        <p className="notice">
          Kết quả được xác nhận từ hệ thống thanh toán. Nếu giao dịch chưa cập
          nhật, hãy kiểm tra lại trạng thái.
        </p>
      )}
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
        query.data && (
          <>
            <ErrorMessage
              error={query.error}
              retry={() => {
                void query.refetch();
              }}
            />
            <BookingContent
              booking={query.data}
              actions={actions}
              stale={query.isError}
            />
          </>
        )
      )}
    </main>
  );
}
function BookingContent({
  booking,
  actions,
  stale,
}: {
  booking: Booking;
  actions: ReturnType<typeof useBookingActions>;
  stale: boolean;
}) {
  const { session } = useServices();
  const { current } = useSession();
  const isOwner = current?.user.userId === booking.userId;
  const show = useBookingShowtime(booking.ticket ? 0 : booking.showtimeId);
  const seatMap = useSeatMap(
    booking.seats.every((s) => s.rowCode && s.seatNumber)
      ? 0
      : booking.showtimeId,
    false,
  );
  const movie = useMovie(show.data?.movieId ?? 0);
  const timer = useCountdown(
    booking.status === "PENDING_PAYMENT" ? booking.expiredAt : undefined,
  );
  const [confirm, setConfirm] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const pending = booking.status === "PENDING_PAYMENT";
  const busy = actions.pending;
  async function pay() {
    setError(null);
    const sessionId = session.getSnapshot()?.sessionId;
    try {
      const payment = await actions.pay.mutateAsync();
      if (!sessionId || session.getSnapshot()?.sessionId !== sessionId) return;
      if (payment.status === "NOT_REQUIRED") return;
      window.location.assign(paymentUrl(payment.checkoutUrl));
    } catch (error) {
      setError(error);
    }
  }
  return (
    <div className="checkout-grid">
      <section className="surface ticket-detail">
        <div className="ticket-heading">
          <BookingStatus status={booking.status} />
          <span className="muted">#{booking.id}</span>
        </div>
        <h2>
          {booking.ticket?.movieTitle ||
            movie.data?.title ||
            `Suất chiếu #${booking.showtimeId}`}
        </h2>
        {booking.ticket ? (
          <p className="muted">
            {formatDate(booking.ticket.startTime)} ·{" "}
            {formatTime(booking.ticket.startTime)} · {booking.ticket.cinemaName}{" "}
            · {booking.ticket.screenName}
          </p>
        ) : (
          show.data && (
            <p className="muted">
              {formatDate(show.data.startTime)} ·{" "}
              {formatTime(show.data.startTime)} ·{" "}
              <VenueName
                cinemaId={show.data.cinemaId}
                screenId={show.data.screenId}
              />
            </p>
          )
        )}
        <div className="booking-reference">
          <span>Mã đặt vé</span>
          <strong>{booking.bookingCode}</strong>
        </div>
        <dl className="summary-lines">
          {booking.seats.map((seat) => {
            const physical = seatMap.data?.seats.find(
              (item) => item.seatId === seat.seatId,
            );
            return (
              <div key={seat.seatId}>
                <dt>
                  Ghế{" "}
                  {seat.rowCode && seat.seatNumber
                    ? `${seat.rowCode}${seat.seatNumber}`
                    : physical
                      ? `${physical.rowCode}${physical.seatNumber}`
                      : `#${seat.seatId}`}
                  {seat.seatTypeName ? ` · ${seat.seatTypeName}` : ""}
                </dt>
                <dd>{formatMoney(seat.price)}</dd>
              </div>
            );
          })}
          {booking.combos?.map((combo) => (
            <div key={combo.comboId}>
              <dt>
                {combo.name} × {combo.quantity}
              </dt>
              <dd>{formatMoney(combo.price * combo.quantity)}</dd>
            </div>
          ))}
          {booking.discountAmount !== undefined &&
            !booking.vouchers?.length && (
              <div>
                <dt>Giảm giá</dt>
                <dd>−{formatMoney(booking.discountAmount)}</dd>
              </div>
            )}
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
        {booking.status === "CONFIRMED" && (
          <p className="notice">
            Đặt vé đã được xác nhận. Giữ lại mã đặt vé để đối chiếu tại rạp.
          </p>
        )}
        {booking.status === "EXPIRED" && (
          <p className="notice">
            Thời gian giữ ghế đã hết. Nếu đã chuyển khoản, hãy kiểm tra lại
            trạng thái để xem kết quả xử lý.
          </p>
        )}
        {booking.payment && (
          <p className="muted">
            Giao dịch #{booking.payment.orderCode} ·{" "}
            {booking.payment.status === "SUCCESS"
              ? "Đã nhận thanh toán"
              : booking.payment.status === "REFUNDED"
                ? "Đã hoàn tiền"
                : booking.payment.status === "FAILED"
                  ? "Giao dịch đã đóng"
                  : "Chờ thanh toán"}
          </p>
        )}
        {booking.payment?.status === "SUCCESS" &&
          booking.status !== "CONFIRMED" &&
          booking.status !== "SHOWTIME_CANCELLED" && (
            <p className="notice">
              Đã ghi nhận tiền nhưng vé chưa được xác nhận. Vui lòng liên hệ rạp
              để kiểm tra việc hoàn tiền.
            </p>
          )}
        {booking.status === "CONFIRMED" && (
          <>
            <p className="muted">
              Đã vào rạp: {booking.admittedCount ?? 0} / {booking.seats.length}{" "}
              khách
            </p>
            <TicketQr key={booking.id} bookingId={booking.id} />
          </>
        )}
        {booking.status === "SHOWTIME_CANCELLED" && (
          <p className="notice">
            Rạp đã hủy suất chiếu này. Kiểm tra{" "}
            <Link to="/vouchers">ví ưu đãi</Link> để xem voucher bù của bạn.
          </p>
        )}
      </section>
      <aside className="surface order-summary">
        <h2>{pending ? "Hoàn tất thanh toán" : "Trạng thái giao dịch"}</h2>
        {pending && isOwner && (
          <>
            <div className="hold-timer">
              <span>Thời gian giữ ghế còn lại</span>
              <strong>{timer.label}</strong>
            </div>
            {timer.expired && (
              <p className="notice">
                Đã hết thời gian giữ ghế. Đang chờ hệ thống cập nhật trạng thái.
              </p>
            )}
            <p className="muted">
              {booking.totalAmount === 0
                ? "Voucher đã thanh toán toàn bộ đơn. Bấm xác nhận để hệ thống xuất vé."
                : "Bạn sẽ được chuyển đến PayOS để thanh toán. Không chuyển tiền lại nếu giao dịch trước đang được xử lý."}
            </p>
          </>
        )}
        <ErrorMessage
          error={error || actions.sync.error || actions.cancel.error}
        />
        {pending && isOwner && (
          <Button
            variant="primary"
            pending={actions.pay.isPending}
            disabled={busy || timer.expired || stale}
            onClick={() => {
              void pay();
            }}
          >
            {booking.totalAmount === 0
              ? "Xác nhận vé bằng voucher"
              : "Thanh toán qua PayOS"}
          </Button>
        )}
        {isOwner && (
          <Button
            pending={actions.sync.isPending}
            disabled={busy}
            onClick={() => {
              setError(null);
              actions.sync.mutate();
            }}
          >
            Kiểm tra trạng thái
          </Button>
        )}
        {pending && isOwner && (
          <Button
            variant="ghost"
            disabled={busy || stale}
            onClick={() => setConfirm(true)}
          >
            Hủy đặt vé
          </Button>
        )}
        {!pending && (
          <Link className="button" to="/">
            Chọn phim khác
          </Link>
        )}
        {pending && isOwner && (
          <BookingCombos
            booking={booking}
            disabled={
              busy ||
              timer.expired ||
              stale ||
              booking.payment?.status === "PENDING"
            }
            busy={actions.combos.isPending}
            save={actions.combos.mutateAsync}
          />
        )}
        {pending && isOwner && (
          <BookingVouchers
            booking={booking}
            actions={actions}
            disabled={
              busy ||
              timer.expired ||
              stale ||
              booking.payment?.status === "PENDING"
            }
          />
        )}
      </aside>
      {confirm && (
        <Dialog
          title="Hủy đặt vé?"
          busy={actions.cancel.isPending}
          onClose={() => setConfirm(false)}
        >
          <p className="dialog-description">
            Các ghế đang giữ sẽ được trả lại. Chỉ đơn chưa thanh toán mới có thể
            hủy.
          </p>
          <ErrorMessage error={actions.cancel.error} />
          <div className="dialog-actions">
            <Button disabled={busy} onClick={() => setConfirm(false)}>
              Giữ đặt vé
            </Button>
            <Button
              variant="danger"
              pending={actions.cancel.isPending}
              onClick={async () => {
                try {
                  await actions.cancel.mutateAsync();
                  setConfirm(false);
                } catch {
                  /* Keep confirmation open with the mutation error. */
                }
              }}
            >
              Xác nhận hủy
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
