package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingView(
    Integer id,
    String bookingCode,
    Integer userId,
    Long showtimeId,
    BookingStatus status,
    BigDecimal totalAmount,
    LocalDateTime bookingTime,
    LocalDateTime expiredAt,
    List<SeatLine> seats,
    PaymentView payment
) {
    public record SeatLine(Integer seatId, BigDecimal price, SeatHoldStatus status) {}

    public static BookingView of(Booking booking, PaymentTransaction latestPayment) {
        return new BookingView(booking.getId(), booking.getBookingCode().value(), booking.getUserId(),
            booking.getShowtimeId(), booking.getStatus(), booking.getTotalAmount(), booking.getBookingTime(),
            booking.getExpiredAt(),
            booking.getSeats().stream().map(s -> new SeatLine(s.getSeatId(), s.getPrice(), s.getStatus())).toList(),
            latestPayment == null ? null : PaymentView.of(latestPayment));
    }
}
