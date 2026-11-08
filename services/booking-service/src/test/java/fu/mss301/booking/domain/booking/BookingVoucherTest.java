package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingVoucherRemoved;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingVoucherTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final LocalDate LAST_DAY = NOW.toLocalDate().plusDays(10);
    private static final int STANDARD = 1;
    private static final int VIP = 2;
    private static final TicketDetails TICKET = new TicketDetails("Dune", null, "Rạp Hà Nội", "Phòng 1",
        NOW.plusDays(1), NOW.plusDays(1).plusHours(2));

    @Test
    void ticketExchangePaysForOneWholeSeat() {
        Booking booking = booking();
        booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), 2, NOW);
        assertEquals(new BigDecimal("210000.00"), booking.subtotal());
        assertEquals(new BigDecimal("120000.00"), booking.discountAmount());
        assertEquals(new BigDecimal("90000.00"), booking.totalAmount());
        assertEquals(2, booking.vouchers().getFirst().seatId());
        assertEquals(new BigDecimal("120000.00"), booking.vouchers().getFirst().discountAmount());
    }

    @Test
    void ticketExchangeNeedsAFreeSeatOfTheRightType() {
        Booking booking = booking();
        assertThrows(DomainException.class, () -> booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), null, NOW));
        assertThrows(DomainException.class, () -> booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), 99, NOW));
        var vipOnly = new VoucherOffer(1, "EMVIP", VoucherKind.TICKET_EXCHANGE, DiscountType.PERCENT, new BigDecimal("100"),
            null, VIP, null, LAST_DAY);
        assertThrows(DomainException.class, () -> booking.applyVoucher(vipOnly, 1, NOW));
        booking.applyVoucher(vipOnly, 2, NOW);
        assertThrows(DomainException.class, () -> booking.applyVoucher(offer(2, VoucherKind.TICKET_EXCHANGE), 2, NOW));
        booking.applyVoucher(offer(2, VoucherKind.TICKET_EXCHANGE), 1, NOW);
        assertEquals(new BigDecimal("0.00"), booking.totalAmount());
    }

    @Test
    void onlyAnExchangeVoucherTakesASeat() {
        Booking booking = booking();
        assertThrows(DomainException.class, () -> booking.applyVoucher(percent(1, VoucherKind.TICKET_DISCOUNT, "10"), 1, NOW));
    }

    @Test
    void percentDiscountRoundsDownToWholeDong() {
        Booking booking = hold(seat(1, "99999", STANDARD));
        booking.applyVoucher(percent(1, VoucherKind.TICKET_DISCOUNT, "15"), null, NOW);
        // 15% of 99,999 is 14,999.85
        assertEquals(new BigDecimal("14999.00"), booking.discountAmount());
        assertEquals(new BigDecimal("85000.00"), booking.totalAmount());
    }

    @Test
    void fixedTicketDiscountStopsAtTheTicketPriceAndTheRestIsLost() {
        Booking booking = booking();
        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        booking.applyVoucher(fixed(1, VoucherKind.TICKET_DISCOUNT, "500000", null), null, NOW);
        assertEquals(new BigDecimal("210000.00"), booking.discountAmount());
        assertEquals(new BigDecimal("85000.00"), booking.totalAmount());
    }

    @Test
    void ticketDiscountCountsOnlySeatsWithoutAnExchangeVoucher() {
        Booking booking = booking();
        booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), 2, NOW);
        booking.applyVoucher(percent(2, VoucherKind.TICKET_DISCOUNT, "50"), null, NOW);
        assertEquals(new BigDecimal("45000.00"), booking.vouchers().getLast().discountAmount());
        assertEquals(new BigDecimal("45000.00"), booking.totalAmount());
    }

    @Test
    void ticketDiscountLimitedToASeatTypeCountsOnlyThoseSeats() {
        Booking booking = booking();
        booking.applyVoucher(new VoucherOffer(1, "EMVIP", VoucherKind.TICKET_DISCOUNT, DiscountType.PERCENT,
            new BigDecimal("50"), null, VIP, null, LAST_DAY), null, NOW);
        assertEquals(new BigDecimal("60000.00"), booking.discountAmount());
    }

    @Test
    void theMinimumIsComparedWithWhatTheVoucherDiscounts() {
        Booking booking = booking();
        booking.replaceCombos(List.of(combo(5, 4, "85000")), NOW);
        var needsMore = fixed(1, VoucherKind.TICKET_DISCOUNT, "20000", "300000");
        assertThrows(DomainException.class, () -> booking.applyVoucher(needsMore, null, NOW));
        booking.applyVoucher(fixed(2, VoucherKind.TICKET_DISCOUNT, "20000", "210000"), null, NOW);
        assertEquals(new BigDecimal("20000.00"), booking.discountAmount());
    }

    @Test
    void eachDiscountKindIsUsedOncePerBooking() {
        Booking booking = booking();
        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        booking.applyVoucher(percent(1, VoucherKind.TICKET_DISCOUNT, "10"), null, NOW);
        assertThrows(DomainException.class, () -> booking.applyVoucher(percent(2, VoucherKind.TICKET_DISCOUNT, "10"), null, NOW));
        booking.applyVoucher(percent(3, VoucherKind.COMBO_DISCOUNT, "10"), null, NOW);
        assertThrows(DomainException.class, () -> booking.applyVoucher(percent(4, VoucherKind.COMBO_DISCOUNT, "10"), null, NOW));
        booking.applyVoucher(value(5, "1000"), null, NOW);
        assertThrows(DomainException.class, () -> booking.applyVoucher(value(6, "1000"), null, NOW));
        assertThrows(InvalidStateTransitionException.class,
            () -> booking.applyVoucher(percent(1, VoucherKind.TICKET_DISCOUNT, "10"), null, NOW));
        assertEquals(3, booking.vouchers().size());
    }

    @Test
    void comboExchangeCoversOneServingPerVoucher() {
        Booking booking = booking();
        assertThrows(DomainException.class, () -> booking.applyVoucher(comboExchange(1, 5), null, NOW));
        booking.replaceCombos(List.of(combo(5, 2, "85000"), combo(6, 1, "40000")), NOW);
        booking.applyVoucher(comboExchange(1, 5), null, NOW);
        booking.applyVoucher(comboExchange(2, 5), null, NOW);
        assertThrows(DomainException.class, () -> booking.applyVoucher(comboExchange(3, 5), null, NOW));
        assertEquals(new BigDecimal("170000.00"), booking.discountAmount());
        assertEquals(new BigDecimal("250000.00"), booking.totalAmount());
    }

    @Test
    void fewerServingsTakeOffTheLatestComboExchangeVoucher() {
        Booking booking = persisted(booking());
        booking.replaceCombos(List.of(combo(5, 2, "85000")), NOW);
        booking.applyVoucher(comboExchange(1, 5), null, NOW);
        booking.applyVoucher(comboExchange(2, 5), null, NOW);
        booking.pullEvents();
        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        assertEquals(List.of(1), booking.vouchers().stream().map(BookingVoucher::voucherId).toList());
        assertEquals(List.of(new BookingVoucherRemoved(42, 2)), booking.pullEvents());
        assertEquals(new BigDecimal("210000.00"), booking.totalAmount());
    }

    @Test
    void comboDiscountFollowsTheCombosAndComesOffBelowItsMinimum() {
        Booking booking = persisted(booking());
        booking.replaceCombos(List.of(combo(5, 2, "85000")), NOW);
        booking.applyVoucher(new VoucherOffer(1, "EMCOMBO", VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT,
            new BigDecimal("20"), new BigDecimal("150000"), null, null, LAST_DAY), null, NOW);
        assertEquals(new BigDecimal("34000.00"), booking.discountAmount());

        booking.replaceCombos(List.of(combo(5, 3, "85000")), NOW);
        assertEquals(new BigDecimal("51000.00"), booking.discountAmount());
        assertEquals(List.of(), booking.pullEvents());

        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        assertTrue(booking.vouchers().isEmpty());
        assertEquals(new BigDecimal("0.00"), booking.discountAmount());
        assertEquals(new BigDecimal("295000.00"), booking.totalAmount());
        assertEquals(List.of(new BookingVoucherRemoved(42, 1)), booking.pullEvents());
    }

    @Test
    void comboDiscountComesOffWhenEveryComboIsRemoved() {
        Booking booking = persisted(booking());
        booking.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        booking.applyVoucher(fixed(1, VoucherKind.COMBO_DISCOUNT, "10000", null), null, NOW);
        booking.replaceCombos(List.of(), NOW);
        assertTrue(booking.vouchers().isEmpty());
        assertEquals(List.of(new BookingVoucherRemoved(42, 1)), booking.pullEvents());
    }

    @Test
    void comboDiscountCountsOnlyServingsWithoutAnExchangeVoucher() {
        Booking booking = booking();
        booking.replaceCombos(List.of(combo(5, 2, "85000")), NOW);
        booking.applyVoucher(percent(1, VoucherKind.COMBO_DISCOUNT, "50"), null, NOW);
        booking.applyVoucher(comboExchange(2, 5), null, NOW);
        assertEquals(new BigDecimal("42500.00"), booking.vouchers().getFirst().discountAmount());
        assertEquals(new BigDecimal("127500.00"), booking.discountAmount());
    }

    @Test
    void valueVoucherComesLastAndNeverMakesTheTotalNegative() {
        Booking booking = booking();
        booking.applyVoucher(value(1, "1000000"), null, NOW);
        assertEquals(new BigDecimal("210000.00"), booking.discountAmount());
        assertEquals(new BigDecimal("0.00"), booking.totalAmount());
        booking.applyVoucher(percent(2, VoucherKind.TICKET_DISCOUNT, "10"), null, NOW);
        assertEquals(new BigDecimal("189000.00"), booking.vouchers().getFirst().discountAmount());
        assertEquals(new BigDecimal("0.00"), booking.totalAmount());

        Booking other = booking();
        other.applyVoucher(value(1, "100000"), null, NOW);
        other.applyVoucher(percent(2, VoucherKind.TICKET_DISCOUNT, "50"), null, NOW);
        // The ticket discount is priced first, then the value voucher takes what is left of its amount.
        assertEquals(new BigDecimal("100000.00"), other.vouchers().getFirst().discountAmount());
        assertEquals(new BigDecimal("105000.00"), other.vouchers().getLast().discountAmount());
        assertEquals(new BigDecimal("5000.00"), other.totalAmount());
    }

    @Test
    void aRejectedVoucherLeavesTheBookingUntouched() {
        Booking booking = booking();
        booking.applyVoucher(fixed(1, VoucherKind.TICKET_DISCOUNT, "10000", "210000"), null, NOW.plusMinutes(1));
        // Exchanging a seat would drop the ticket amount below the discount voucher's minimum.
        assertThrows(DomainException.class, () -> booking.applyVoucher(offer(2, VoucherKind.TICKET_EXCHANGE), 1, NOW.plusMinutes(2)));
        assertEquals(1, booking.vouchers().size());
        assertEquals(new BigDecimal("200000.00"), booking.totalAmount());
        assertEquals(NOW.plusMinutes(1), booking.updatedAt());
    }

    @Test
    void removingAVoucherRestoresThePriceAndRaisesAnEvent() {
        Booking booking = persisted(booking());
        booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), 1, NOW);
        assertThrows(DomainException.class, () -> booking.removeVoucher(99, NOW));
        booking.removeVoucher(1, NOW);
        assertEquals(new BigDecimal("210000.00"), booking.totalAmount());
        assertEquals(new BigDecimal("0.00"), booking.discountAmount());
        assertEquals(List.of(new BookingVoucherRemoved(42, 1)), booking.pullEvents());
    }

    @Test
    void anExpiredVoucherCannotBeAppliedAndComesOffBeforePayment() {
        Booking booking = persisted(booking());
        var endedYesterday = new VoucherOffer(3, "EMOLD", VoucherKind.VALUE, DiscountType.FIXED, new BigDecimal("1000"),
            null, null, null, NOW.toLocalDate().minusDays(1));
        assertThrows(DomainException.class, () -> booking.applyVoucher(endedYesterday, null, NOW));
        var lastDayToday = new VoucherOffer(1, "EMTODAY", VoucherKind.TICKET_EXCHANGE, DiscountType.PERCENT,
            new BigDecimal("100"), null, null, null, NOW.toLocalDate());
        booking.applyVoucher(lastDayToday, 1, NOW);
        booking.applyVoucher(value(2, "5000"), null, NOW);
        assertFalse(booking.hasExpiredVoucherOn(NOW.toLocalDate()));
        assertFalse(booking.removeExpiredVouchers(NOW.withHour(23).withMinute(59)));

        LocalDateTime afterMidnight = NOW.toLocalDate().plusDays(1).atStartOfDay();
        assertTrue(booking.hasExpiredVoucherOn(afterMidnight.toLocalDate()));
        assertTrue(booking.removeExpiredVouchers(afterMidnight));
        assertEquals(List.of(2), booking.vouchers().stream().map(BookingVoucher::voucherId).toList());
        assertEquals(new BigDecimal("205000.00"), booking.totalAmount());
        assertEquals(List.of(new BookingVoucherRemoved(42, 1)), booking.pullEvents());
    }

    @Test
    void vouchersChangeOnlyWhileAwaitingPaymentWithinTheHold() {
        Booking expired = booking();
        assertThrows(InvalidStateTransitionException.class,
            () -> expired.applyVoucher(value(1, "1000"), null, NOW.plusMinutes(5)));
        Booking confirmed = persisted(booking());
        confirmed.applyVoucher(value(1, "1000"), null, NOW);
        confirmed.confirm(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> confirmed.removeVoucher(1, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> confirmed.removeExpiredVouchers(NOW.plusDays(30)));
    }

    @Test
    void aRestoredBookingKeepsItsVouchers() {
        Booking booking = booking();
        booking.applyVoucher(offer(1, VoucherKind.TICKET_EXCHANGE), 1, NOW);
        Booking restored = persisted(booking);
        assertEquals(new BigDecimal("90000.00"), restored.discountAmount());
        restored.replaceCombos(List.of(combo(5, 1, "85000")), NOW);
        assertEquals(new BigDecimal("205000.00"), restored.totalAmount());
        assertTrue(restored.hasVoucher(1));
        assertThrows(UnsupportedOperationException.class, () -> restored.vouchers().clear());
    }

    private static Booking booking() {
        return hold(seat(1, "90000", STANDARD), seat(2, "120000", VIP));
    }

    private static Booking hold(SeatSelection... seats) {
        return Booking.hold(7, 3L, TICKET, List.of(seats), 8, NOW, Duration.ofMinutes(5));
    }

    private static SeatSelection seat(int id, String price, int type) {
        return new SeatSelection(id, null, new BigDecimal(price), "A", id, type == VIP ? "VIP" : "Thường", type);
    }

    private static ComboSelection combo(int id, int quantity, String price) {
        return new ComboSelection(id, "Combo " + id, quantity, new BigDecimal(price).setScale(2));
    }

    private static VoucherOffer offer(int id, VoucherKind kind) {
        return new VoucherOffer(id, "EM" + id, kind, DiscountType.PERCENT, new BigDecimal("100"), null, null, null, LAST_DAY);
    }

    private static VoucherOffer comboExchange(int id, int comboId) {
        return new VoucherOffer(id, "EM" + id, VoucherKind.COMBO_EXCHANGE, DiscountType.PERCENT, new BigDecimal("100"),
            null, null, comboId, LAST_DAY);
    }

    private static VoucherOffer percent(int id, VoucherKind kind, String value) {
        return new VoucherOffer(id, "EM" + id, kind, DiscountType.PERCENT, new BigDecimal(value), null, null, null, LAST_DAY);
    }

    private static VoucherOffer fixed(int id, VoucherKind kind, String value, String min) {
        return new VoucherOffer(id, "EM" + id, kind, DiscountType.FIXED, new BigDecimal(value),
            min == null ? null : new BigDecimal(min), null, null, LAST_DAY);
    }

    private static VoucherOffer value(int id, String amount) {
        return new VoucherOffer(id, "EM" + id, VoucherKind.VALUE, DiscountType.FIXED, new BigDecimal(amount), null, null,
            null, LAST_DAY);
    }

    private static Booking persisted(Booking booking) {
        return Booking.restore(42, booking.userId(), booking.showtimeId(), booking.bookingCode(), booking.ticket(),
            booking.bookingTime(), booking.totalAmount(), booking.discountAmount(), booking.status(), booking.expiredAt(), 0,
            booking.createdAt(), booking.updatedAt(), booking.seats(), booking.combos(), booking.vouchers());
    }
}
