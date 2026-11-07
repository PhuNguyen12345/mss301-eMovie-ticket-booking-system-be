import { Link } from "react-router-dom";
import { BookingStatus } from "./BookingStatus";
import { formatDate, formatMoney, formatTime } from "../../lib/date";
import type { Booking } from "./api";
export function BookingCard({
  booking,
  admin = false,
}: {
  booking: Booking;
  admin?: boolean;
}) {
  return (
    <Link to={`/bookings/${booking.id}`} className="booking-card">
      <div>
        <BookingStatus status={booking.status} />
        <h2>{booking.ticket?.movieTitle ?? booking.bookingCode}</h2>
        <p>
          {booking.bookingCode}
          {admin ? ` · Khách #${booking.userId}` : ""}
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
