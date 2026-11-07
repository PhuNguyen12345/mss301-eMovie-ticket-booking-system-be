package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.util.Comparator;

public class BookingQueryService {
    private final BookingRepository bookings;
    private final BookingQueries queries;
    private final PaymentRepository payments;
    private final Transactions transactions;

    public BookingQueryService(BookingRepository bookings, BookingQueries queries, PaymentRepository payments,
                               Transactions transactions) {
        this.bookings = bookings;
        this.queries = queries;
        this.payments = payments;
        this.transactions = transactions;
    }

    public BookingPage<BookingView> myBookings(Integer userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size 1..100");
        return transactions.read(() -> queries.findByUser(userId, page, size)
            .map(booking -> BookingView.of(booking, latestPayment(booking))));
    }

    /** Owners see their own bookings, admins see all; anyone else gets 404 so ids are not probed. */
    public BookingView getBooking(Integer requesterId, boolean admin, Integer bookingId) {
        return transactions.read(() -> {
            Booking booking = bookings.findById(bookingId)
                .filter(b -> admin || b.isOwnedBy(requesterId))
                .orElseThrow(BookingNotFoundException::new);
            return BookingView.of(booking, latestPayment(booking));
        });
    }

    private PaymentTransaction latestPayment(Booking booking) {
        return payments.findByBookingId(booking.getId()).stream()
            .max(Comparator.comparing(PaymentTransaction::getId)).orElse(null);
    }
}
