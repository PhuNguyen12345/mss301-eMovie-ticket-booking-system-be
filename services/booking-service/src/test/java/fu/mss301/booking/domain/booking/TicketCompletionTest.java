package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketCompletionTest {
    private static final LocalDateTime START = LocalDateTime.of(2027, 1, 21, 18, 0);
    private static final TicketDetails SOLD = new TicketDetails("Dune", "https://img/dune.jpg", "Rạp Hà Nội",
        "Phòng 1", START, START.plusHours(2));
    private static final TicketDetails CURRENT = new TicketDetails("Dune: Part Two", null, "Rạp Hà Nội mới",
        "Phòng 9", START.plusHours(1), START.plusHours(3), 8, 2);

    @Test
    void aBookingWithoutATicketTakesTheCurrentOne() {
        Booking booking = stored(null);
        assertTrue(booking.completeTicket(CURRENT));
        assertEquals(CURRENT, booking.ticket());
    }

    @Test
    void aSoldTicketOnlyGainsTheIds() {
        Booking booking = stored(SOLD);
        assertTrue(booking.completeTicket(CURRENT));
        assertEquals(new TicketDetails("Dune", "https://img/dune.jpg", "Rạp Hà Nội", "Phòng 1", START,
            START.plusHours(2), 8, 2), booking.ticket());
    }

    @Test
    void aCompleteTicketIsLeftAlone() {
        var complete = new TicketDetails("Dune", null, "Rạp", "Phòng 1", START, START.plusHours(2), 5, 6);
        Booking booking = stored(complete);
        assertFalse(booking.completeTicket(CURRENT));
        assertEquals(complete, booking.ticket());
    }

    @Test
    void theCurrentTicketMustCarryTheIds() {
        Booking booking = stored(SOLD);
        assertThrows(DomainException.class, () -> booking.completeTicket(SOLD));
        assertThrows(DomainException.class, () -> booking.completeTicket(null));
        assertThrows(DomainException.class, () -> booking.completeTicket(
            new TicketDetails("Dune", null, "Rạp", "Phòng 1", START, START.plusHours(2), 8, null)));
        assertEquals(SOLD, booking.ticket());
    }

    private static Booking stored(TicketDetails ticket) {
        var seat = BookingSeat.restore(1, 3L, null, 101, new BigDecimal("90000"), "A", 1, "Ghế thường", 1,
            SeatHoldStatus.BOOKED, START, START);
        return Booking.restore(42, 7, 3L, new BookingCode("BK270101000000ABCDEF"), ticket, START.minusDays(1),
            new BigDecimal("90000.00"), BigDecimal.ZERO.setScale(2), BookingStatus.CONFIRMED, START.minusDays(1),
            0, START.minusDays(1), START.minusDays(1), List.of(seat), List.of(), List.of(), List.of(), null, null);
    }
}
