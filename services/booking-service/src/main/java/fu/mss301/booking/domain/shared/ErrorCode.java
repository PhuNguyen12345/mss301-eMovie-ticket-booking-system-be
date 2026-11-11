package fu.mss301.booking.domain.shared;

/**
 * Stable reasons a request is refused, sent to clients next to the English message so they can show their own
 * text. Renaming a value breaks clients; add new values instead.
 */
public enum ErrorCode {
    // Generic, by HTTP status, for errors without a specific reason.
    INVALID_REQUEST, NOT_FOUND, CONFLICT, CONCURRENT_UPDATE,
    // Check-in at the door.
    CHECK_IN_NOT_OPEN, CHECK_IN_NOT_PAID, CHECK_IN_TOO_MANY, TICKET_ALREADY_USED, BOOKING_NOT_FOUND,
    // Holding seats.
    SHOWTIME_NOT_OPEN, SEAT_UNAVAILABLE, SEAT_OUT_OF_SERVICE, SEAT_LIMIT,
    // A pending booking and its payment.
    BOOKING_NOT_PENDING, HOLD_EXPIRED, PAYMENT_LINK_OPEN, PAYMENT_IN_PROGRESS, PAYMENT_ATTEMPTS_EXCEEDED,
    // Combos.
    COMBO_NOT_FOUND, COMBO_UNAVAILABLE, COMBO_QUANTITY,
    // Vouchers.
    VOUCHER_NOT_FOUND, VOUCHER_ALREADY_APPLIED, VOUCHER_EXPIRED, VOUCHER_NOT_STARTED, VOUCHER_IN_USE,
    VOUCHER_INACTIVE, VOUCHER_NOT_OWNED, VOUCHER_SEAT_REQUIRED, VOUCHER_MIN_ORDER, VOUCHER_NOT_APPLICABLE,
    VOUCHER_MIN_PAYMENT
}
