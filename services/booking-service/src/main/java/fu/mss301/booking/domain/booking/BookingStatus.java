package fu.mss301.booking.domain.booking;

/** {@code SHOWTIME_CANCELLED}: the booking was paid, then the cinema cancelled its showtime. */
public enum BookingStatus {
    PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED, SHOWTIME_CANCELLED
}
