package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.common.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class BookingQueryService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final TransactionRunner tx;

    public BookingQueryService(BookingRepository bookings, PaymentRepository payments, TransactionRunner tx) {
        this.bookings = bookings;
        this.payments = payments;
        this.tx = tx;
    }

    public PageResponseDTO<BookingView> myBookings(Integer userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size 1..100");
        return tx.call(() -> {
            var result = bookings.findByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
            return new PageResponseDTO<>(result.getContent().stream().map(b -> BookingView.of(b, latestPayment(b))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
        });
    }

    /** Owners see their own bookings, admins see all; anyone else gets 404 so ids are not probed. */
    public BookingView getBooking(Integer requesterId, boolean admin, Integer bookingId) {
        return tx.call(() -> {
            Booking booking = bookings.findById(bookingId)
                .filter(b -> admin || b.isOwnedBy(requesterId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
            return BookingView.of(booking, latestPayment(booking));
        });
    }

    private PaymentTransaction latestPayment(Booking booking) {
        return payments.findByBookingId(booking.getId()).stream()
            .max(Comparator.comparing(PaymentTransaction::getId)).orElse(null);
    }
}
