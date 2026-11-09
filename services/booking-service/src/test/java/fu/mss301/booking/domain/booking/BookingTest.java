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
    private static final TicketDetails TICKET = new TicketDetails("Dune", "https://img/dune.jpg", "Rạp Hà Nội", "Phòng 1",
        NOW.plusDays(1), NOW.plusDays(1).plusHours(2));

    @Test
    void holdComputesTotalAndHoldsEverySeat() {
        Booking booking = hold(seat(1, "90000"), seat(2, "120000.50"));
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.status());
        assertEquals(new BigDecimal("210000.50"), booking.totalAmount());
        assertEquals(NOW.plus(TTL), booking.expiredAt());
        assertTrue(booking.bookingCode().value().startsWith("BK"));
        assertEquals(TICKET, booking.ticket());
        assertEquals("A", booking.seats().getFirst().rowCode());
        assertEquals(1, booking.seats().getFirst().seatNumber());
        assertEquals("Ghế thường", booking.seats().getFirst().seatTypeName());
        assertTrue(booking.seats().stream().allMatch(s -> s.status() == SeatHoldStatus.HELD));
    }

    @Test
    void holdRejectsEmptyDuplicateAndTooManySeats() {
        assertThrows(DomainException.class, () -> hold());
        assertThrows(DomainException.class, () -> hold(seat(1, "1"), seat(1, "1")));
        assertThrows(DomainException.class, () -> Booking.hold(7, 3L, TICKET, List.of(seat(1, "1"), seat(2, "1")), 1, NOW, TTL));
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
        var seat = BookingSeat.restore(5, 3L, null, 1, new BigDecimal("90000.00"), null, null, null, null, SeatHoldStatus.RELEASED,
            NOW, NOW);
        Booking booking = Booking.restore(42, 7, 3L, new BookingCode("BK260101000000ABCDEF"), null, NOW,
            new BigDecimal("90000.00"), BigDecimal.ZERO, BookingStatus.EXPIRED, NOW.minusYears(1), 3, NOW, NOW, List.of(seat),
            List.of(), List.of(), List.of(), null, null);
        assertEquals(BookingStatus.EXPIRED, booking.status());
        assertEquals(3, booking.version());
        assertEquals(5, booking.seats().getFirst().id());
        assertNull(booking.ticket());
        assertEquals(List.of(), booking.pullEvents());
    }

    @Test
    void combosAddToTheTotal() {
        Booking booking = hold(seat(1, "90000"), seat(2, "120000"));
        booking.replaceCombos(List.of(combo(5, 2, "85000"), combo(6, 1, "40000")), NOW.plusMinutes(1));
        assertEquals(new BigDecimal("420000.00"), booking.totalAmount());
        assertEquals(List.of(5, 6), booking.combos().stream().map(BookingCombo::comboId).toList());
        assertEquals(NOW.plusMinutes(1), booking.updatedAt());
    }

    @Test
    void replacingRecalculatesAndAnEmptyListRemovesAllCombos() {
        Booking booking = hold(seat(1, "90000"));
        booking.replaceCombos(List.of(combo(5, 2, "85000")), NOW);
        booking.replaceCombos(List.of(combo(6, 1, "40000")), NOW);
        assertEquals(new BigDecimal("130000.00"), booking.totalAmount());
        booking.replaceCombos(List.of(), NOW);
        assertEquals(new BigDecimal("90000.00"), booking.totalAmount());
        assertTrue(booking.combos().isEmpty());
    }

    @Test
    void aKeptComboKeepsItsLineAndTakesTheNewQuantityAndPrice() {
        Booking booking = persisted(hold(seat(1, "90000")));
        var stored = BookingCombo.restore(77, 5, "Bắp nước", 2, new BigDecimal("85000.00"));
        Booking restored = Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(),
            booking.ticket(), booking.bookingTime(), new BigDecimal("260000.00"), BigDecimal.ZERO, booking.status(),
            booking.expiredAt(), 0, booking.createdAt(), booking.updatedAt(), booking.seats(), List.of(stored), List.of(),
            List.of(), null, null);
        restored.replaceCombos(List.of(combo(5, 3, "95000"), combo(6, 1, "40000")), NOW);
        assertEquals(77, restored.combos().getFirst().id());
        assertEquals(3, restored.combos().getFirst().quantity());
        assertEquals(new BigDecimal("95000.00"), restored.combos().getFirst().price());
        assertNull(restored.combos().getLast().id());
        assertEquals(new BigDecimal("415000.00"), restored.totalAmount());
    }

    @Test
    void invalidCombosAreRejectedAndLeaveTheBookingUntouched() {
        Booking booking = hold(seat(1, "90000"));
        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        assertThrows(DomainException.class, () -> booking.replaceCombos(List.of(combo(6, 0, "40000")), NOW));
        assertThrows(DomainException.class, () -> booking.replaceCombos(List.of(combo(6, -1, "40000")), NOW));
        assertThrows(DomainException.class, () -> booking.replaceCombos(List.of(combo(6, 21, "40000")), NOW));
        assertThrows(DomainException.class, () -> booking.replaceCombos(List.of(combo(6, 1, "40000"), combo(6, 2, "40000")), NOW));
        assertThrows(DomainException.class, () -> booking.replaceCombos(List.of(combo(6, 2, "99999999")), NOW));
        assertEquals(new BigDecimal("175000.00"), booking.totalAmount());
        assertEquals(List.of(5), booking.combos().stream().map(BookingCombo::comboId).toList());
    }

    @Test
    void combosCanOnlyChangeWhileAwaitingPaymentAndBeforeTheHoldExpires() {
        Booking confirmed = persisted(hold(seat(1, "90000")));
        confirmed.confirm(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> confirmed.replaceCombos(List.of(), NOW));

        Booking overdue = hold(seat(1, "90000"));
        assertThrows(InvalidStateTransitionException.class, () -> overdue.replaceCombos(List.of(), NOW.plus(TTL)));
    }

    @Test
    void aBookingNeedsCompleteTicketDetails() {
        assertThrows(DomainException.class, () -> Booking.hold(7, 3L, null, List.of(seat(1, "1")), 8, NOW, TTL));
        assertThrows(DomainException.class, () -> new TicketDetails(" ", null, "Rạp", "Phòng", NOW, NOW));
        assertThrows(DomainException.class, () -> new TicketDetails("Dune", null, null, "Phòng", NOW, NOW));
        assertThrows(DomainException.class, () -> new TicketDetails("Dune", null, "Rạp", "", NOW, NOW));
        assertThrows(DomainException.class, () -> new TicketDetails("Dune", null, "Rạp", "Phòng", null, NOW));
        assertNull(new TicketDetails("Dune", null, "Rạp", "Phòng", NOW, NOW).moviePosterUrl());
    }

    @Test
    void combosCannotBeModifiedFromOutside() {
        Booking booking = hold(seat(1, "90000"));
        assertThrows(UnsupportedOperationException.class, () -> booking.combos().clear());
    }

    private static ComboSelection combo(int id, int quantity, String price) {
        return new ComboSelection(id, "Combo " + id, quantity, new BigDecimal(price).setScale(2));
    }

    private static Booking hold(SeatSelection... seats) {
        return Booking.hold(7, 3L, TICKET, List.of(seats), 8, NOW, TTL);
    }

    private static SeatSelection seat(int id, String price) {
        return new SeatSelection(id, null, new BigDecimal(price), "A", id, "Ghế thường", null);
    }

    private static Booking persisted(Booking booking) {
        return Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(), booking.ticket(), booking.bookingTime(),
            booking.totalAmount(), booking.discountAmount(), booking.status(), booking.expiredAt(), 0, booking.createdAt(),
            booking.updatedAt(), booking.seats(), booking.combos(), booking.vouchers(), booking.checkIns(), booking.showtimeCancelledAt(),
            booking.compensatedAt());
    }
}
