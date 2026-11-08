package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The cinema cancelled a showtime: its bookings waiting for payment are cancelled and its paid bookings are
 * compensated, through the events each change raises. Handling the same showtime again changes nothing more.
 */
public class ShowtimeCancellationUseCase {
    private static final System.Logger log = System.getLogger(ShowtimeCancellationUseCase.class.getName());
    private final ShowtimeCatalog showtimes;
    private final BookingRepository bookings;
    private final Transactions transactions;
    private final DomainEvents events;
    private final Clock clock;

    public ShowtimeCancellationUseCase(ShowtimeCatalog showtimes, BookingRepository bookings, Transactions transactions,
                                       DomainEvents events, Clock clock) {
        this.showtimes = showtimes;
        this.bookings = bookings;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public ShowtimeCancellationView handle(Long showtimeId) {
        // showtime-service is the record of the cancellation, so a wrong or repeated call can do no harm.
        if (!showtimes.getShowtime(showtimeId).cancelled()) {
            throw new InvalidStateTransitionException("Showtime " + showtimeId + " is not cancelled");
        }
        int pending = 0;
        int paid = 0;
        int failed = 0;
        for (Integer bookingId : transactions.read(() -> bookings.findOpenIdsByShowtime(showtimeId))) {
            try {
                BookingStatus before = transactions.write(() -> cancel(bookingId));
                if (before == BookingStatus.PENDING_PAYMENT) pending++;
                if (before == BookingStatus.CONFIRMED) paid++;
            } catch (RuntimeException exception) {
                failed++;
                log.log(Level.WARNING, () -> "Could not cancel booking " + bookingId + " of cancelled showtime "
                    + showtimeId + ": " + exception.getMessage());
            }
        }
        return new ShowtimeCancellationView(showtimeId, pending, paid, failed);
    }

    /** @return the status before, or null when the booking did not change */
    private BookingStatus cancel(Integer bookingId) {
        var booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null) return null;
        BookingStatus before = booking.status();
        if (!booking.cancelForShowtime(LocalDateTime.now(clock))) return null;
        bookings.save(booking);
        events.publish(booking.pullEvents());
        return before;
    }
}
