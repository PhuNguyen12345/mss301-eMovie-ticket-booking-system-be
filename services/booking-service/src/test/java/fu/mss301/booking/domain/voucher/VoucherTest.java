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
