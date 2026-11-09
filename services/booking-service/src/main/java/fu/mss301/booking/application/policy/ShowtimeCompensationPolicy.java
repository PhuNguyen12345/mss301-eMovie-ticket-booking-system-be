package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.booking.ShowtimeCompensationService;
import fu.mss301.booking.domain.booking.event.BookingShowtimeCancelled;

import java.lang.System.Logger.Level;

/** A paid booking lost its showtime -> compensate the customer. */
public class ShowtimeCompensationPolicy {
    private static final System.Logger log = System.getLogger(ShowtimeCompensationPolicy.class.getName());
    private final ShowtimeCompensationService compensation;

    public ShowtimeCompensationPolicy(ShowtimeCompensationService compensation) {
        this.compensation = compensation;
    }

    public void on(BookingShowtimeCancelled event) {
        try {
            compensation.compensate(event.bookingId());
        } catch (RuntimeException exception) {
            // The booking stays not compensated; the maintenance job tries again.
            log.log(Level.ERROR, () -> "Compensating booking " + event.bookingId() + " failed", exception);
        }
    }
}
