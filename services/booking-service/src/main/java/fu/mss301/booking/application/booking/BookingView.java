package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import fu.mss301.booking.domain.booking.TicketDetails;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookingView(
    Integer id,
    String bookingCode,
    Integer userId,
    Long showtimeId,
    BookingStatus status,
    /** Seats plus combos, before vouchers. */
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    LocalDateTime bookingTime,
    LocalDateTime expiredAt,
    TicketView ticket,
    List<SeatLine> seats,
    List<ComboItem> combos,
    List<VoucherLine> vouchers,
    PaymentView payment
) {
    /** Null for bookings made before ticket details were stored. */
    public record TicketView(String movieTitle, String moviePosterUrl, LocalDateTime startTime, LocalDateTime endTime,
                             String cinemaName, String screenName) {
        static TicketView of(TicketDetails ticket) {
            return ticket == null ? null : new TicketView(ticket.movieTitle(), ticket.moviePosterUrl(),
                ticket.startTime(), ticket.endTime(), ticket.cinemaName(), ticket.screenName());
        }
    }

    public record SeatLine(Integer seatId, String rowCode, Integer seatNumber, String seatTypeName, BigDecimal price,
                           SeatHoldStatus status) {}

    public record ComboItem(Integer comboId, String name, Integer quantity, BigDecimal price) {}

    /** @param seatId the seat a ticket exchange voucher pays for, otherwise null */
    public record VoucherLine(Integer voucherId, String code, VoucherKind kind, Integer seatId, Integer comboId,
                              BigDecimal discountAmount, LocalDate expiresOn) {}

    public static BookingView of(Booking booking, PaymentTransaction latestPayment) {
        return new BookingView(booking.id(), booking.bookingCode().value(), booking.userId(),
            booking.showtimeId(), booking.status(), booking.subtotal(), booking.discountAmount(), booking.totalAmount(),
            booking.bookingTime(),
            booking.expiredAt(), TicketView.of(booking.ticket()),
            booking.seats().stream().map(s -> new SeatLine(s.seatId(), s.rowCode(), s.seatNumber(), s.seatTypeName(),
                s.price(), s.status())).toList(),
            booking.combos().stream().map(c -> new ComboItem(c.comboId(), c.name(), c.quantity(), c.price())).toList(),
            booking.vouchers().stream().map(v -> new VoucherLine(v.voucherId(), v.code(), v.kind(), v.seatId(),
                v.comboId(), v.discountAmount(), v.expiresOn())).toList(),
            latestPayment == null ? null : PaymentView.of(latestPayment));
    }
}
