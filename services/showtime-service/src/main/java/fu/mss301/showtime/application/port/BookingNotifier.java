package fu.mss301.showtime.application.port;

/**
 * Tells booking-service that a showtime was cancelled, so it can cancel pending bookings and compensate paid ones.
 * Called after the cancellation committed. It must never throw: booking-service checks the showtime itself and
 * admins can re-run the handling, so a lost call is recovered, while a failed cancellation would not be.
 */
public interface BookingNotifier {
    void showtimeCancelled(Long showtimeId);
}
