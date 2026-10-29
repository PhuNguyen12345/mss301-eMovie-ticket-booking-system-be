package fu.mss301.booking.domain.booking;

/** Persisted state of one seat inside a booking; RELEASED rows no longer block the seat. */
public enum SeatHoldStatus {
    HELD, BOOKED, RELEASED
}
