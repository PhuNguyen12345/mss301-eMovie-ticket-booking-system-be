package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingCheckInTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final LocalDateTime START = NOW.plusDays(1);
    private static final CheckInWindow WINDOW = new CheckInWindow(Duration.ofMinutes(15), Duration.ofMinutes(20));
    private static final TicketDetails TICKET = new TicketDetails("Dune", null, "Rạp", "Phòng 1", START, START.plusHours(2));

    @Test
    void onlyAPaidBookingCanBeCheckedIn() {
        Booking pending = persisted(hold(3));
        assertThrows(InvalidStateTransitionException.class, () -> pending.checkIn(null, 1, START, WINDOW, START));
        Booking cancelled = persisted(hold(3));
        cancelled.cancelBy(7, NOW);
        assertThrows(InvalidStateTransitionException.class, () -> cancelled.checkIn(null, 1, START, WINDOW, START));
    }

    @Test
    void theDoorOpens15MinutesBeforeAndClosesAfter20Minutes() {
        assertThrows(InvalidStateTransitionException.class,
            () -> paid(1).checkIn(null, 1, START, WINDOW, START.minusMinutes(15).minusSeconds(1)));
        assertDoesNotThrow(() -> paid(1).checkIn(null, 1, START, WINDOW, START.minusMinutes(15)));
        assertDoesNotThrow(() -> paid(1).checkIn(null, 1, START, WINDOW, START.plusMinutes(20)));
        var late = assertThrows(InvalidStateTransitionException.class,
            () -> paid(1).checkIn(null, 1, START, WINDOW, START.plusMinutes(20).plusSeconds(1)));
        assertTrue(late.getMessage().contains("2027-01-21T17:45") && late.getMessage().contains("2027-01-21T18:20"),
            late.getMessage());
    }

    @Test
    void oneCodeLetsAGroupInAFewAtATime() {
        Booking booking = paid(3);
        booking.checkIn(2, 1, START, WINDOW, START.minusMinutes(10));
        assertEquals(2, booking.admittedCount());
        assertEquals(1, booking.remainingToAdmit());
        booking.checkIn(null, 9, START, WINDOW, START.plusMinutes(5));
        assertEquals(3, booking.admittedCount());
        assertEquals(0, booking.remainingToAdmit());
        assertEquals(List.of(2, 1), booking.checkIns().stream().map(BookingCheckIn::quantity).toList());
        assertEquals(9, booking.checkIns().getLast().checkedInBy());
        assertEquals(START.plusMinutes(5), booking.checkIns().getLast().checkedInAt());
        assertThrows(AlreadyCheckedInException.class, () -> booking.checkIn(null, 1, START, WINDOW, START));
        assertThrows(AlreadyCheckedInException.class, () -> booking.checkIn(1, 1, START, WINDOW, START));
    }

    @Test
    void moreThanTheSeatsLeftOrNobodyIsRefused() {
        Booking booking = paid(2);
        booking.checkIn(1, 1, START, WINDOW, START);
        assertThrows(DomainException.class, () -> booking.checkIn(2, 1, START, WINDOW, START));
        assertThrows(DomainException.class, () -> booking.checkIn(0, 1, START, WINDOW, START));
        assertThrows(DomainException.class, () -> booking.checkIn(-1, 1, START, WINDOW, START));
        assertEquals(1, booking.admittedCount());
        assertThrows(DomainException.class, () -> paid(1).checkIn(null, 1, null, WINDOW, START));
        assertThrows(DomainException.class, () -> new CheckInWindow(Duration.ofMinutes(-1), Duration.ZERO));
    }

    @Test
    void storedCheckInsCountAndCannotBeChangedFromOutside() {
        Booking booking = paid(3);
        var stored = BookingCheckIn.restore(5, 2, 1, START.minusMinutes(5));
        Booking restored = Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(),
            booking.ticket(), booking.bookingTime(), booking.totalAmount(), booking.discountAmount(), booking.status(),
            booking.expiredAt(), 1, booking.createdAt(), booking.updatedAt(), booking.seats(), booking.combos(),
            booking.vouchers(), List.of(stored));
        assertEquals(2, restored.admittedCount());
        assertEquals(1, restored.remainingToAdmit());
        assertThrows(UnsupportedOperationException.class, () -> restored.checkIns().clear());
    }

    private static Booking paid(int seats) {
        Booking booking = persisted(hold(seats));
        booking.confirm(NOW);
        return booking;
    }

    private static Booking hold(int seats) {
        var selections = java.util.stream.IntStream.rangeClosed(1, seats)
            .mapToObj(id -> new SeatSelection(id, null, new BigDecimal("90000"), "A", id, "Thường", 1)).toList();
        return Booking.hold(7, 3L, TICKET, selections, 8, NOW, Duration.ofMinutes(5));
    }

    private static Booking persisted(Booking booking) {
        return Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(), booking.ticket(),
            booking.bookingTime(), booking.totalAmount(), booking.discountAmount(), booking.status(), booking.expiredAt(), 0,
            booking.createdAt(), booking.updatedAt(), booking.seats(), booking.combos(), booking.vouchers(),
            booking.checkIns());
    }
}
