package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        requireValidPage(page, size);
        return transactions.read(() -> withLatestPayments(queries.findByUser(userId, page, size)));
    }

    public BookingPage<BookingView> search(BookingSearch search, int page, int size) {
        requireValidPage(page, size);
        if (search.from() != null && search.to() != null && !search.from().isBefore(search.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        var normalized = new BookingSearch(search.status(), search.showtimeId(), search.userId(),
            normalizeCode(search.code()), search.from(), search.to());
        return transactions.read(() -> withLatestPayments(queries.search(normalized, page, size)));
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

    /** Loads the payments of a whole page at once, so the query count does not grow with the page size. */
    private BookingPage<BookingView> withLatestPayments(BookingPage<Booking> bookings) {
        var ids = bookings.content().stream().map(Booking::id).toList();
        Map<Integer, PaymentTransaction> latest = ids.isEmpty() ? Map.of() : payments.findByBookingIds(ids).stream()
            .collect(Collectors.toMap(PaymentTransaction::bookingId, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(PaymentTransaction::id))));
        return bookings.map(booking -> BookingView.of(booking, latest.get(booking.id())));
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size 1..100");
    }

    /** Booking codes are generated in upper case. */
    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.strip().toUpperCase(Locale.ROOT);
    }

    private PaymentTransaction latestPayment(Booking booking) {
        return payments.findByBookingId(booking.id()).stream()
            .max(Comparator.comparing(PaymentTransaction::id)).orElse(null);
    }
}
