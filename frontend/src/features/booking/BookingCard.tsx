import { Link } from "react-router-dom";
import { BookingStatus } from "./BookingStatus";
import { formatDate, formatMoney, formatTime } from "../../lib/date";
import type { Booking } from "./api";
import type { Customer } from "../admin/api";
export function BookingCard({
  booking,
  admin = false,
  customer,
}: {
  booking: Booking;
  admin?: boolean;
  /** Who placed the order, shown to staff; undefined while it loads or when unknown. */
  customer?: Customer;
}) {
  return (
    <Link
      to={admin ? `/admin/orders/${booking.id}` : `/bookings/${booking.id}`}
      className="booking-card"
    >
      <div>
        <BookingStatus status={booking.status} />
        <h2>{booking.ticket?.movieTitle ?? booking.bookingCode}</h2>
        <p>
          {booking.bookingCode}
          {admin &&
            (customer
              ? ` · ${customer.fullName} · ${customer.email}`
              : ` · Khách #${booking.userId}`)}
        </p>
        {booking.ticket && (
          <p className="muted">
            {booking.ticket.cinemaName} · {booking.ticket.screenName} ·{" "}
            {formatDate(booking.ticket.startTime)}{" "}
            {formatTime(booking.ticket.startTime)}
          </p>
        )}
        <p className="muted">
          {booking.seats
            .map((s) =>
              s.rowCode && s.seatNumber
                ? `${s.rowCode}${s.seatNumber}`
                : `#${s.seatId}`,
            )
            .join(", ")}{" "}
          · Đặt ngày {formatDate(booking.bookingTime)}
        </p>
      </div>
      <div>
        <strong>{formatMoney(booking.totalAmount)}</strong>
        <span className="text-link">Xem chi tiết →</span>
      </div>
    </Link>
  );
}
