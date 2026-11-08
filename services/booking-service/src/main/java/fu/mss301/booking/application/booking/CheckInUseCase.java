package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.TicketAlreadyUsedException;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.AlreadyCheckedInException;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.CheckInWindow;
import fu.mss301.booking.domain.shared.DomainException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Door staff scan a booking code and let in everyone it paid for, or part of a group arriving apart. */
public class CheckInUseCase {
    private final BookingRepository bookings;
    private final ShowtimeCatalog showtimes;
    private final CheckInWindow window;
    private final Transactions transactions;
    private final Clock clock;

    public CheckInUseCase(BookingRepository bookings, ShowtimeCatalog showtimes, CheckInWindow window,
                          Transactions transactions, Clock clock) {
        this.bookings = bookings;
        this.showtimes = showtimes;
        this.window = window;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** @param quantity how many people go in now; null lets in everyone not yet in */
    public CheckInView checkIn(Integer staffId, String bookingCode, Integer quantity) {
        if (bookingCode == null || bookingCode.isBlank()) throw new DomainException("Booking code is required");
        String code = bookingCode.strip().toUpperCase(Locale.ROOT);
        var found = transactions.read(() -> bookings.findByCode(code)).orElseThrow(BookingNotFoundException::new);
        // The copied ticket fixes the time a sold ticket shows; only bookings older than that copy ask showtime-service.
        LocalDateTime start = found.ticket() != null ? found.ticket().startTime()
            : showtimes.getShowtime(found.showtimeId()).startTime();
        try {
            return transactions.write(() -> {
                // The row lock makes two scanners at the same door take turns.
                var booking = bookings.findByCodeForUpdate(code).orElseThrow(BookingNotFoundException::new);
                booking.checkIn(quantity, staffId, start, window, LocalDateTime.now(clock));
                return CheckInView.of(bookings.save(booking), start);
            });
        } catch (AlreadyCheckedInException exception) {
            throw new TicketAlreadyUsedException(transactions.read(() -> bookings.findByCode(code)
                .map(booking -> CheckInView.of(booking, start)).orElseThrow(BookingNotFoundException::new)));
        }
    }
}
