package fu.mss301.booking.application.booking;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the door staff see after scanning a code.
 *
 * @param movieTitle null for bookings made before ticket details were stored
 * @param remaining  how many people can still go in with this code
 */
public record CheckInView(String bookingCode, BookingStatus status, String movieTitle, String cinemaName,
                          String screenName, LocalDateTime startTime, List<Seat> seats, int seatCount,
                          int admittedCount, int remaining, List<Scan> checkIns) {
    /** @param rowCode null for bookings made before seat details were stored */
    public record Seat(Integer seatId, String rowCode, Integer seatNumber, String seatTypeName) {}

    public record Scan(int quantity, LocalDateTime checkedInAt, Integer checkedInBy) {}

    static CheckInView of(Booking booking, LocalDateTime startTime) {
        var ticket = booking.ticket();
        return new CheckInView(booking.bookingCode().value(), booking.status(),
            ticket == null ? null : ticket.movieTitle(), ticket == null ? null : ticket.cinemaName(),
            ticket == null ? null : ticket.screenName(), startTime,
            booking.seats().stream().map(s -> new Seat(s.seatId(), s.rowCode(), s.seatNumber(), s.seatTypeName())).toList(),
            booking.seats().size(), booking.admittedCount(), booking.remainingToAdmit(),
            booking.checkIns().stream().map(c -> new Scan(c.quantity(), c.checkedInAt(), c.checkedInBy())).toList());
    }
}
