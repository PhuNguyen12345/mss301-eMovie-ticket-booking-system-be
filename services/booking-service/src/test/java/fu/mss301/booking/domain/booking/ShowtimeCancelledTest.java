package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingShowtimeCancelled;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShowtimeCancelledTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final LocalDateTime START = NOW.plusDays(1);
    private static final Duration NOTICE = Duration.ofHours(2);
    private static final TicketDetails TICKET = new TicketDetails("Dune", null, "Rạp", "Phòng 1", START, START.plusHours(2));
    private static final CheckInWindow WINDOW = new CheckInWindow(Duration.ofMinutes(15), Duration.ofMinutes(20));

    @Test
    void aBookingWaitingForPaymentIsCancelledLikeTheCustomerWould() {
        Booking booking = persisted(hold(1, 2));
        assertTrue(booking.cancelForShowtime(NOW));
        assertEquals(BookingStatus.CANCELLED, booking.status());
        assertTrue(booking.seats().stream().allMatch(s -> s.status() == SeatHoldStatus.RELEASED));
        assertNull(booking.showtimeCancelledAt());
        assertEquals(List.of(new BookingCancelled(42)), booking.pullEvents());
    }

    @Test
    void aPaidBookingFreesItsSeatsAndIsOwedCompensationOnce() {
        Booking booking = paid(hold(1, 2));
        assertTrue(booking.cancelForShowtime(NOW.plusHours(1)));
        assertEquals(BookingStatus.SHOWTIME_CANCELLED, booking.status());
        assertEquals(NOW.plusHours(1), booking.showtimeCancelledAt());
        assertTrue(booking.seats().stream().allMatch(s -> s.status() == SeatHoldStatus.RELEASED));
        assertEquals(List.of(new BookingShowtimeCancelled(42)), booking.pullEvents());

        assertFalse(booking.cancelForShowtime(NOW.plusHours(2)), "the same cancellation again changes nothing");
        assertEquals(NOW.plusHours(1), booking.showtimeCancelledAt());
        assertEquals(List.of(), booking.pullEvents());
        assertThrows(InvalidStateTransitionException.class, () -> booking.checkIn(null, 1, START, WINDOW, START));
        assertFalse(booking.isPayableAt(NOW));

        assertTrue(booking.markCompensated(NOW.plusHours(1)));
        assertFalse(booking.markCompensated(NOW.plusHours(3)));
        assertEquals(NOW.plusHours(1), booking.compensatedAt());
    }

    @Test
    void finishedBookingsAreLeftAlone() {
        Booking cancelled = persisted(hold(1));
        cancelled.cancelBy(7, NOW);
        assertFalse(cancelled.cancelForShowtime(NOW));
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        Booking expired = persisted(hold(1));
        expired.expire(NOW.plusMinutes(5));
        assertFalse(expired.cancelForShowtime(NOW.plusMinutes(6)));
        assertThrows(InvalidStateTransitionException.class, () -> expired.markCompensated(NOW));
    }

    @Test
    void shortNoticeIsLessThanTwoHoursBeforeTheStartOrLater() {
        assertFalse(cancelledAt(START.minusHours(2)).wasCancelledAtShortNotice(START, NOTICE));
        assertTrue(cancelledAt(START.minusHours(2).plusSeconds(1)).wasCancelledAtShortNotice(START, NOTICE));
        assertTrue(cancelledAt(START.plusMinutes(30)).wasCancelledAtShortNotice(START, NOTICE));
        assertThrows(InvalidStateTransitionException.class,
            () -> paid(hold(1)).wasCancelledAtShortNotice(START, NOTICE));
    }

    @Test
    void earlyNoticeGivesTheMoneyBackSplitIntoCombosAndTheRest() {
        Booking booking = persisted(hold(1, 2));
        booking.replaceCombos(List.of(new ComboSelection(5, "Bắp nước", 2, new BigDecimal("85000.00")),
            new ComboSelection(6, "Nước", 1, new BigDecimal("40000.00"))), NOW);
        booking.confirm(NOW);
        // 180,000 of seats and 210,000 of combos were paid.
        var plan = ShowtimeCompensation.of(booking, new BigDecimal("390000.00"), false);
        assertEquals(new BigDecimal("210000"), plan.comboRefund());
        assertEquals(new BigDecimal("180000"), plan.valueRefund());
        assertEquals(new BigDecimal("180000"), plan.moneyRefund());
        assertFalse(plan.freeTicket());
        assertEquals(BigDecimal.ZERO, plan.apologyCombo());
    }

    @Test
    void shortNoticeAddsAFreeTicketAndTheDearestComboOnlyWhenCombosWereBought() {
        Booking withCombos = persisted(hold(1));
        withCombos.replaceCombos(List.of(new ComboSelection(5, "Bắp nước", 1, new BigDecimal("85000.00")),
            new ComboSelection(6, "Nước", 3, new BigDecimal("40000.00"))), NOW);
        var plan = ShowtimeCompensation.of(withCombos, new BigDecimal("295000.00"), true);
        assertTrue(plan.freeTicket());
        assertEquals(new BigDecimal("85000"), plan.apologyCombo());

        var noCombos = ShowtimeCompensation.of(persisted(hold(1)), new BigDecimal("90000.00"), true);
        assertTrue(noCombos.freeTicket());
        assertEquals(BigDecimal.ZERO, noCombos.apologyCombo());
        assertEquals(BigDecimal.ZERO, noCombos.comboRefund());
        assertEquals(new BigDecimal("90000"), noCombos.valueRefund());
    }

    @Test
    void vouchersCountOnlyWhatWasPaidAndValueVouchersComeBackAsValue() {
        Booking booking = persisted(hold(1, 2));
        booking.replaceCombos(List.of(new ComboSelection(5, "Bắp nước", 2, new BigDecimal("85000.00"))), NOW);
        booking.applyVoucher(offer(1, VoucherKind.COMBO_EXCHANGE, 5), null, NOW);
        booking.applyVoucher(offer(2, VoucherKind.TICKET_EXCHANGE, null), 1, NOW);
        booking.applyVoucher(new VoucherOffer(3, "EM3", VoucherKind.VALUE, DiscountType.FIXED, new BigDecimal("50000"),
            null, null, null, START.toLocalDate()), null, NOW);
        // 350,000 less 85,000 combo, 90,000 seat and 50,000 value voucher leaves 125,000 paid.
        assertEquals(new BigDecimal("125000.00"), booking.totalAmount());
        var plan = ShowtimeCompensation.of(booking, booking.totalAmount(), false);
        assertEquals(new BigDecimal("85000"), plan.comboRefund());
        assertEquals(new BigDecimal("40000"), plan.moneyRefund());
        assertEquals(new BigDecimal("90000"), plan.valueRefund());
    }

    @Test
    void aBookingVouchersPaidInFullGetsNoMoneyBack() {
        Booking booking = persisted(hold(1));
        booking.replaceCombos(List.of(new ComboSelection(5, "Bắp nước", 1, new BigDecimal("85000.00"))), NOW);
        var plan = ShowtimeCompensation.of(booking, BigDecimal.ZERO, false);
        assertEquals(BigDecimal.ZERO, plan.comboRefund());
        assertEquals(BigDecimal.ZERO, plan.valueRefund());
    }

    private static VoucherOffer offer(int id, VoucherKind kind, Integer comboId) {
        return new VoucherOffer(id, "EM" + id, kind, DiscountType.PERCENT, new BigDecimal("100"), null, null, comboId,
            START.toLocalDate());
    }

    private static Booking cancelledAt(LocalDateTime when) {
        Booking booking = paid(hold(1));
        booking.cancelForShowtime(when);
        return booking;
    }

    private static Booking paid(Booking held) {
        Booking booking = persisted(held);
        booking.confirm(NOW);
        booking.pullEvents();
        return booking;
    }

    private static Booking hold(int... seats) {
        var selections = java.util.Arrays.stream(seats)
            .mapToObj(id -> new SeatSelection(id, null, new BigDecimal("90000"), "A", id, "Thường", 1)).toList();
        return Booking.hold(7, 3L, TICKET, selections, 8, NOW, Duration.ofMinutes(5));
    }

    private static Booking persisted(Booking booking) {
        return Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(), booking.ticket(),
            booking.bookingTime(), booking.totalAmount(), booking.discountAmount(), booking.status(), booking.expiredAt(), 0,
            booking.createdAt(), booking.updatedAt(), booking.seats(), booking.combos(), booking.vouchers(),
            booking.checkIns(), booking.showtimeCancelledAt(), booking.compensatedAt());
    }
}
