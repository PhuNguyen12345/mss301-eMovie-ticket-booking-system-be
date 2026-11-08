package fu.mss301.booking.application.booking;

/**
 * What handling a cancelled showtime did.
 *
 * @param cancelledPending bookings waiting for payment that were cancelled
 * @param cancelledPaid    paid bookings whose customers were compensated
 * @param failed           bookings that could not be handled now; calling again retries them
 */
public record ShowtimeCancellationView(Long showtimeId, int cancelledPending, int cancelledPaid, int failed) {}
