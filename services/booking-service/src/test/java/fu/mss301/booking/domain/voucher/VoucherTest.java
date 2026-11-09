package fu.mss301.booking.domain.voucher;

import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VoucherTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 31);

    @Test
    void fixedDiscountKeepsItsAmount() {
        var terms = discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "20000");
        assertEquals(new BigDecimal("20000.00"), terms.discountValue());
    }

    @Test
    void percentDiscountMustBeAWholeNumberUpTo100() {
        assertDoesNotThrow(() -> discount(VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT, "100"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT, "0"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT, "101"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT, "10.5"));
    }

    @Test
    void fixedDiscountMustBeAPositiveWholeAmount() {
        assertThrows(DomainException.class, () -> discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "0"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "1000.50"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.TICKET_DISCOUNT, null, "1000"));
        assertThrows(DomainException.class, () -> discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, null));
    }

    @Test
    void exchangeVoucherAlwaysCoversEverything() {
        var terms = new VoucherTerms(VoucherKind.TICKET_EXCHANGE, null, null, START, END, null, 2, null);
        assertEquals(DiscountType.PERCENT, terms.discountType());
        assertEquals(new BigDecimal("100.00"), terms.discountValue());
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.TICKET_EXCHANGE, DiscountType.FIXED,
            new BigDecimal("1000"), START, END, null, null, null));
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.TICKET_EXCHANGE, DiscountType.PERCENT,
            new BigDecimal("50"), START, END, null, null, null));
    }

    @Test
    void comboExchangeMustNameItsCombo() {
        assertDoesNotThrow(() -> new VoucherTerms(VoucherKind.COMBO_EXCHANGE, null, null, START, END, null, null, 5));
        assertThrows(DomainException.class,
            () -> new VoucherTerms(VoucherKind.COMBO_EXCHANGE, null, null, START, END, null, null, null));
    }

    @Test
    void limitsOnlyFitTheKindsTheyMeanSomethingFor() {
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.COMBO_DISCOUNT, DiscountType.FIXED,
            new BigDecimal("1000"), START, END, null, 2, null));
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.COMBO_DISCOUNT, DiscountType.FIXED,
            new BigDecimal("1000"), START, END, null, null, 5));
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.TICKET_EXCHANGE, null, null, START, END,
            new BigDecimal("1000"), null, null));
    }

    @Test
    void endDateCannotPrecedeStartDate() {
        assertThrows(DomainException.class, () -> new VoucherTerms(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED,
            new BigDecimal("1000"), END, START, null, null, null));
    }

    @Test
    void adminsCannotCreateOrChangeIntoValueVouchers() {
        var value = new VoucherTerms(VoucherKind.VALUE, DiscountType.FIXED, new BigDecimal("1000"), START, END, null, null, null);
        assertThrows(DomainException.class, () -> Voucher.create(VoucherCode.generate(), value, NOW));
        assertThrows(DomainException.class, () -> voucher().update(value, NOW));
    }

    @Test
    void assigningGivesTheCodeOneOwner() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertEquals(7, voucher.claim().userId());
        assertEquals(ClaimStatus.AVAILABLE, voucher.claim().status());
        assertFalse(voucher.isClaimableOn(NOW.toLocalDate()));
        assertThrows(InvalidStateTransitionException.class, () -> voucher.assignTo(8, NOW));
        assertEquals(7, voucher.claim().userId());
    }

    @Test
    void anInactiveOrExpiredVoucherCannotBeAssigned() {
        var inactive = voucher();
        inactive.deactivate(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> inactive.assignTo(7, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> voucher().assignTo(7, END.plusDays(1).atStartOfDay()));
    }

    @Test
    void theEndDateIsStillUsable() {
        assertTrue(voucher().isClaimableOn(END));
        assertFalse(voucher().isClaimableOn(END.plusDays(1)));
    }

    @Test
    void termsAreFrozenOnceOwned() {
        var voucher = voucher();
        var terms = discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "5000");
        voucher.update(terms, NOW);
        assertEquals(terms, voucher.terms());
        voucher.assignTo(7, NOW);
        assertThrows(InvalidStateTransitionException.class, () -> voucher.update(discount(VoucherKind.TICKET_DISCOUNT,
            DiscountType.FIXED, "9000"), NOW));
        assertEquals(terms, voucher.terms());
    }

    @Test
    void ownerSeesWhatTheVoucherMeansToday() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertEquals(VoucherAvailability.AVAILABLE, voucher.availabilityOn(END));
        assertEquals(VoucherAvailability.EXPIRED, voucher.availabilityOn(END.plusDays(1)));
        voucher.deactivate(NOW);
        assertEquals(VoucherAvailability.INACTIVE, voucher.availabilityOn(END));
    }

    @Test
    void onlyTheOwnerCanHoldAnUsableVoucherForOneBooking() {
        var unowned = voucher();
        assertThrows(InvalidStateTransitionException.class, () -> unowned.reserveFor(7, 42, NOW));

        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertThrows(InvalidStateTransitionException.class, () -> voucher.reserveFor(8, 42, NOW));
        voucher.reserveFor(7, 42, NOW);
        assertEquals(ClaimStatus.RESERVED, voucher.claim().status());
        assertEquals(42, voucher.claim().bookingId());
        assertEquals(VoucherAvailability.RESERVED, voucher.availabilityOn(NOW.toLocalDate()));
        assertDoesNotThrow(() -> voucher.reserveFor(7, 42, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> voucher.reserveFor(7, 43, NOW));
    }

    @Test
    void aVoucherCanBeHeldOnlyWhileActiveAndWithinItsDates() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertThrows(DomainException.class, () -> voucher.reserveFor(7, 42, START.minusDays(1).atTime(12, 0)));
        assertThrows(DomainException.class, () -> voucher.reserveFor(7, 42, END.plusDays(1).atStartOfDay()));
        assertDoesNotThrow(() -> voucher.reserveFor(7, 42, END.atTime(23, 59)));
        voucher.releaseFrom(42, NOW);
        voucher.deactivate(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> voucher.reserveFor(7, 42, NOW));
    }

    @Test
    void onlyTheHoldingBookingGivesTheVoucherBack() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertFalse(voucher.releaseFrom(42, NOW));
        voucher.reserveFor(7, 42, NOW);
        assertFalse(voucher.releaseFrom(43, NOW));
        assertTrue(voucher.releaseFrom(42, NOW));
        assertEquals(ClaimStatus.AVAILABLE, voucher.claim().status());
        assertNull(voucher.claim().bookingId());
        assertFalse(voucher.releaseFrom(42, NOW));
        voucher.reserveFor(7, 43, NOW);
    }

    @Test
    void aPaidBookingUsesTheVoucherUpOnce() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        voucher.reserveFor(7, 42, NOW);
        assertFalse(voucher.markUsedBy(43, NOW));
        assertTrue(voucher.markUsedBy(42, NOW.plusMinutes(1)));
        assertEquals(ClaimStatus.USED, voucher.claim().status());
        assertEquals(NOW.plusMinutes(1), voucher.claim().usedAt());
        assertEquals(VoucherAvailability.USED, voucher.availabilityOn(NOW.toLocalDate()));
        assertFalse(voucher.markUsedBy(42, NOW.plusMinutes(2)));
        assertFalse(voucher.releaseFrom(42, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> voucher.reserveFor(7, 44, NOW));
    }

    @Test
    void aVoucherGivenBackByMistakeIsStillUsedByThePaidBooking() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        assertTrue(voucher.markUsedBy(42, NOW));
        assertEquals(42, voucher.claim().bookingId());
    }

    @Test
    void theSystemIssuesValueVouchersStraightToTheirOwner() {
        var voucher = Voucher.forPayment(VoucherCode.of("EMLATE01"), 7, new BigDecimal("90000"), 31, END, NOW);
        assertEquals(VoucherKind.VALUE, voucher.terms().kind());
        assertEquals(DiscountType.FIXED, voucher.terms().discountType());
        assertEquals(new BigDecimal("90000.00"), voucher.terms().discountValue());
        assertEquals(NOW.toLocalDate(), voucher.terms().startDate());
        assertEquals(7, voucher.claim().userId());
        assertEquals(ClaimStatus.AVAILABLE, voucher.claim().status());
        assertEquals(31, voucher.sourcePaymentId());
        assertNull(voucher.sourceBookingId());
        assertDoesNotThrow(() -> voucher.reserveFor(7, 42, NOW));

        var rest = Voucher.forRemainder(VoucherCode.of("EMREST01"), 7, new BigDecimal("150000"), 42, END, NOW);
        assertEquals(42, rest.sourceBookingId());
        assertNull(rest.sourcePaymentId());

        assertThrows(DomainException.class, () -> Voucher.forPayment(VoucherCode.of("EMLATE02"), 7, new BigDecimal("1"), null, END, NOW));
        assertThrows(DomainException.class, () -> Voucher.forRemainder(VoucherCode.of("EMREST02"), 7, new BigDecimal("1"), null, END, NOW));
        assertThrows(DomainException.class, () -> Voucher.forPayment(VoucherCode.of("EMLATE03"), null, new BigDecimal("1"), 31, END, NOW));
        assertThrows(DomainException.class, () -> Voucher.forPayment(VoucherCode.of("EMLATE04"), 7, new BigDecimal("0"), 31, END, NOW));
    }

    @Test
    void aCancelledShowtimeGivesBackAVoucherItsBookingHeldOrUsed() {
        var voucher = voucher();
        voucher.assignTo(7, NOW);
        voucher.reserveFor(7, 42, NOW);
        voucher.markUsedBy(42, NOW);
        assertFalse(voucher.giveBackFrom(43, NOW));
        assertTrue(voucher.giveBackFrom(42, NOW.plusHours(1)));
        assertEquals(ClaimStatus.AVAILABLE, voucher.claim().status());
        assertNull(voucher.claim().bookingId());
        assertNull(voucher.claim().usedAt());
        assertFalse(voucher.giveBackFrom(42, NOW));
        voucher.reserveFor(7, 44, NOW);
        assertTrue(voucher.giveBackFrom(44, NOW));
    }

    @Test
    void theSystemIssuesVouchersForABookingWithTheirReason() {
        var terms = new VoucherTerms(VoucherKind.TICKET_EXCHANGE, null, null, START, END, null, null, null);
        var voucher = Voucher.forBooking(VoucherCode.of("EMSORRY1"), 7, terms, 42, VoucherSourceReason.APOLOGY_TICKET, NOW);
        assertEquals(42, voucher.sourceBookingId());
        assertEquals(VoucherSourceReason.APOLOGY_TICKET, voucher.sourceReason());
        assertEquals(7, voucher.claim().userId());
        assertEquals(VoucherSourceReason.REMAINDER, Voucher.forRemainder(VoucherCode.of("EMREST09"), 7,
            new BigDecimal("1000"), 42, END, NOW).sourceReason());
        assertThrows(DomainException.class,
            () -> Voucher.forBooking(VoucherCode.of("EMSORRY2"), 7, terms, 42, null, NOW));
        assertThrows(DomainException.class,
            () -> Voucher.forBooking(VoucherCode.of("EMSORRY3"), 7, terms, null, VoucherSourceReason.APOLOGY_TICKET, NOW));
    }

    @Test
    void codesAreCaseInsensitiveAndGeneratedOnesAreValid() {
        assertEquals("TET-2027", VoucherCode.of("  tet-2027 ").value());
        assertThrows(DomainException.class, () -> VoucherCode.of("a b"));
        assertThrows(DomainException.class, () -> VoucherCode.of("abc"));
        assertNotEquals(VoucherCode.generate(), VoucherCode.generate());
    }

    private static Voucher voucher() {
        return Voucher.create(VoucherCode.of("TET-2027"), discount(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "20000"), NOW);
    }

    private static VoucherTerms discount(VoucherKind kind, DiscountType type, String value) {
        return new VoucherTerms(kind, type, value == null ? null : new BigDecimal(value), START, END, null, null, null);
    }
}
