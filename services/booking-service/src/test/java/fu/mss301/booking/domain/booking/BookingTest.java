package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final Duration TTL = Duration.ofMinutes(5);

    @Test
    void holdComputesTotalAndHoldsEverySeat() {
        Booking booking = hold(seat(1, "90000"), seat(2, "120000.50"));
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertEquals(new BigDecimal("210000.50"), booking.getTotalAmount());
        assertEquals(NOW.plus(TTL), booking.getExpiredAt());
        assertTrue(booking.getBookingCode().value().startsWith("BK"));
        assertTrue(booking.getSeats().stream().allMatch(s -> s.getStatus() == SeatHoldStatus.HELD));
    }

    @Test
    void holdRejectsEmptyDuplicateAndTooManySeats() {
        assertThrows(DomainException.class, () -> hold());
        assertThrows(DomainException.class, () -> hold(seat(1, "1"), seat(1, "1")));
        assertThrows(DomainException.class, () -> Booking.hold(7, 3L, List.of(seat(1, "1"), seat(2, "1")), 1, NOW, TTL));
        assertThrows(DomainException.class, () -> seat(1, "-1"));
        assertThrows(DomainException.class, () -> seat(1, "1.001"));
    }

    @Test
    void confirmBooksSeatsAndRaisesEvent() {
        Booking booking = persisted(hold(seat(1, "90000")));
        booking.confirm(NOW.plusMinutes(1));
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(SeatHoldStatus.BOOKED, booking.getSeats().getFirst().getStatus());
        assertEquals(List.of(new BookingConfirmed(42)), events(booking));
        assertThrows(InvalidStateTransitionException.class, () -> booking.confirm(NOW));
        assertThrows(InvalidStateTransitionException.class, () -> booking.expire(NOW.plusHours(1)));
    }

    @Test
    void expireOnlyAfterTtlAndReleasesSeats() {
        Booking booking = persisted(hold(seat(1, "90000")));
        assertThrows(InvalidStateTransitionException.class, () -> booking.expire(NOW.plusMinutes(4)));
        assertTrue(booking.isPayableAt(NOW.plusMinutes(4)));
        assertFalse(booking.isPayableAt(NOW.plus(TTL)));
        booking.expire(NOW.plus(TTL));
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(SeatHoldStatus.RELEASED, booking.getSeats().getFirst().getStatus());
        assertEquals(List.of(new BookingExpired(42)), events(booking));
        assertThrows(InvalidStateTransitionException.class, () -> booking.confirm(NOW));
    }

    @Test
    void onlyOwnerCanCancelAPendingBooking() {
        Booking booking = persisted(hold(seat(1, "90000")));
        assertThrows(InvalidStateTransitionException.class, () -> booking.cancelBy(8, NOW));
        booking.cancelBy(7, NOW);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(SeatHoldStatus.RELEASED, booking.getSeats().getFirst().getStatus());
        assertThrows(InvalidStateTransitionException.class, () -> booking.cancelBy(7, NOW));
    }

    @Test
    void seatsCannotBeModifiedFromOutside() {
        Booking booking = hold(seat(1, "90000"));
        assertThrows(UnsupportedOperationException.class, () -> booking.getSeats().clear());
    }

    private static Booking hold(SeatSelection... seats) {
        return Booking.hold(7, 3L, List.of(seats), 8, NOW, TTL);
    }

    private static SeatSelection seat(int id, String price) {
        return new SeatSelection(id, null, new BigDecimal(price));
    }

    private static Booking persisted(Booking booking) {
        ReflectionTestUtils.setField(booking, "id", 42);
        return booking;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> events(Booking booking) {
        return List.copyOf((Collection<Object>) ReflectionTestUtils.invokeMethod(booking, "domainEvents"));
    }
}
