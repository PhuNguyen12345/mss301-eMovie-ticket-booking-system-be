package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Customer gives up a pending booking; open payments are closed by BookingEventPolicy. */
@Service
public class CancelBookingUseCase {
    private final BookingRepository bookings;
    private final TransactionRunner tx;
    private final Clock clock;

    public CancelBookingUseCase(BookingRepository bookings, TransactionRunner tx, Clock clock) {
        this.bookings = bookings;
        this.tx = tx;
        this.clock = clock;
    }

    public BookingView cancel(Integer userId, Integer bookingId) {
        return tx.call(() -> {
            var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
            booking.cancelBy(userId, LocalDateTime.now(clock));
            return BookingView.of(bookings.save(booking), null);
        });
    }
}
