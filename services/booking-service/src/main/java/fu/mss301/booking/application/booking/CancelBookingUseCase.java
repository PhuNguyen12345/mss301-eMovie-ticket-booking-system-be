package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/** Customer gives up a pending booking; open payments are closed by BookingEventPolicy. */
public class CancelBookingUseCase {
    private final BookingRepository bookings;
    private final Transactions transactions;
    private final Clock clock;

    public CancelBookingUseCase(BookingRepository bookings, Transactions transactions, Clock clock) {
        this.bookings = bookings;
        this.transactions = transactions;
        this.clock = clock;
    }

    public BookingView cancel(Integer userId, Integer bookingId) {
        return transactions.write(() -> {
            var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
                .orElseThrow(BookingNotFoundException::new);
            booking.cancelBy(userId, LocalDateTime.now(clock));
            return BookingView.of(bookings.save(booking), null);
        });
    }
}
