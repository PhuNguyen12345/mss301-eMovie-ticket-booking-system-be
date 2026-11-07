package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final Duration TTL = Duration.ofMinutes(5);

    @Test
    void holdComputesTotalAndHoldsEverySeat() {
        Booking booking = hold(seat(1, "90000"), seat(2, "120000.50"));
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.status());
        assertEquals(new BigDecimal("210000.50"), booking.totalAmount());
        assertEquals(NOW.plus(TTL), booking.expiredAt());
        assertTrue(booking.bookingCode().value().startsWith("BK"));
        assertTrue(booking.seats().stream().allMatch(s -> s.status() == SeatHoldStatus.HELD));
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
        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(SeatHoldStatus.BOOKED, booking.seats().getFirst().status());
        assertEquals(List.of(new BookingConfirmed(42)), booking.pullEvents());
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
        assertEquals(BookingStatus.EXPIRED, booking.status());
        assertEquals(SeatHoldStatus.RELEASED, booking.seats().getFirst().status());
        assertEquals(List.of(new BookingExpired(42)), booking.pullEvents());
        assertThrows(InvalidStateTransitionException.class, () -> booking.confirm(NOW));
    }

    @Test
    void onlyOwnerCanCancelAPendingBooking() {
        Booking booking = persisted(hold(seat(1, "90000")));
        assertThrows(InvalidStateTransitionException.class, () -> booking.cancelBy(8, NOW));
        booking.cancelBy(7, NOW);
        assertEquals(BookingStatus.CANCELLED, booking.status());
        assertEquals(SeatHoldStatus.RELEASED, booking.seats().getFirst().status());
        assertThrows(InvalidStateTransitionException.class, () -> booking.cancelBy(7, NOW));
    }

    @Test
    void seatsCannotBeModifiedFromOutside() {
        Booking booking = hold(seat(1, "90000"));
        assertThrows(UnsupportedOperationException.class, () -> booking.seats().clear());
    }

    @Test
    void eventsArePulledOnlyOnce() {
        Booking booking = persisted(hold(seat(1, "90000")));
        booking.cancelBy(7, NOW);
        assertEquals(List.of(new BookingCancelled(42)), booking.pullEvents());
        assertEquals(List.of(), booking.pullEvents());
    }

    @Test
    void restoreLoadsAnOldBookingWithoutReapplyingBusinessRules() {
        var seat = BookingSeat.restore(5, 3L, null, 1, new BigDecimal("90000.00"), SeatHoldStatus.RELEASED, NOW, NOW);
        Booking booking = Booking.restore(42, 7, 3L, new BookingCode("BK260101000000ABCDEF"), NOW,
            new BigDecimal("90000.00"), BookingStatus.EXPIRED, NOW.minusYears(1), 3, NOW, NOW, List.of(seat));
        assertEquals(BookingStatus.EXPIRED, booking.status());
        assertEquals(3, booking.version());
        assertEquals(5, booking.seats().getFirst().id());
        assertEquals(List.of(), booking.pullEvents());
    }

    private static Booking hold(SeatSelection... seats) {
        return Booking.hold(7, 3L, List.of(seats), 8, NOW, TTL);
    }

    private static SeatSelection seat(int id, String price) {
        return new SeatSelection(id, null, new BigDecimal(price));
    }

    private static Booking persisted(Booking booking) {
        return Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(), booking.bookingTime(),
            booking.totalAmount(), booking.status(), booking.expiredAt(), 0, booking.createdAt(), booking.updatedAt(),
            booking.seats());
    }
}
