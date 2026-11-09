package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTransactionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);

    @Test
    void successIsIdempotentAndRaisesOneEvent() {
        PaymentTransaction payment = pending();
        assertTrue(payment.markSucceeded(new BigDecimal("180000"), "FT123", NOW, NOW));
        assertFalse(payment.markSucceeded(new BigDecimal("180000"), "FT123", NOW, NOW));
        assertEquals(PaymentStatus.SUCCESS, payment.status());
        assertEquals("FT123", payment.providerTransactionId());
        assertEquals(List.of(new PaymentSucceeded(5, 9)), payment.pullEvents());
        assertEquals(List.of(), payment.pullEvents());
    }

    @Test
    void successRequiresTheExactAmount() {
        assertThrows(DomainException.class, () -> pending().markSucceeded(new BigDecimal("179999"), "FT1", NOW, NOW));
    }

    @Test
    void lateMoneyOnAFailedAttemptIsStillRecorded() {
        PaymentTransaction payment = pending();
        assertTrue(payment.markFailed(NOW));
        assertFalse(payment.markFailed(NOW));
        assertTrue(payment.markSucceeded(new BigDecimal("180000.00"), "FT9", NOW, NOW));
        assertEquals(PaymentStatus.SUCCESS, payment.status());
    }

    @Test
    void onlyASuccessfulPaymentIsGivenBackAsAVoucherAndOnlyOnce() {
        PaymentTransaction payment = pending();
        assertThrows(InvalidStateTransitionException.class, () -> payment.compensateWithVoucher(77, "late", NOW));
        assertThrows(DomainException.class, () -> payment.compensateWithVoucher(null, "late", NOW));
        payment.markSucceeded(new BigDecimal("180000"), "FT1", NOW, NOW);
        assertTrue(payment.compensateWithVoucher(77, "late", NOW));
        assertFalse(payment.compensateWithVoucher(78, "late again", NOW));
        assertEquals(PaymentStatus.REFUNDED, payment.status());
        assertEquals(1, payment.refunds().size());
        var refund = payment.refunds().getFirst();
        assertEquals(RefundMethod.VOUCHER, refund.method());
        assertEquals(77, refund.voucherId());
        assertEquals(RefundStatus.COMPLETED, refund.status());
        assertEquals(NOW, refund.refundedAt());
        assertEquals(new BigDecimal("180000.00"), refund.amount());
    }

    @Test
    void aPaymentCanBeGivenBackOverSeveralVouchersThatAddUpToIt() {
        PaymentTransaction payment = pending();
        payment.markSucceeded(new BigDecimal("180000"), "FT1", NOW, NOW);
        assertThrows(DomainException.class, () -> payment.compensateWithVouchers(
            List.of(new VoucherShare(1, new BigDecimal("100000"))), "cancelled", NOW));
        assertThrows(DomainException.class, () -> new VoucherShare(1, BigDecimal.ZERO));
        assertTrue(payment.compensateWithVouchers(List.of(new VoucherShare(1, new BigDecimal("85000")),
            new VoucherShare(2, new BigDecimal("95000"))), "cancelled", NOW));
        assertEquals(PaymentStatus.REFUNDED, payment.status());
        assertEquals(List.of(1, 2), payment.refunds().stream().map(Refund::voucherId).toList());
        assertEquals(new BigDecimal("85000.00"), payment.refunds().getFirst().amount());
        assertFalse(payment.compensateWithVouchers(List.of(new VoucherShare(3, new BigDecimal("180000"))), "again", NOW));
    }

    @Test
    void aVoucherRefundIsAlreadyDoneSoItCannotBeCompletedByHand() {
        var given = Refund.restore(4, new BigDecimal("180000.00"), "late", RefundMethod.VOUCHER, 77,
            RefundStatus.COMPLETED, NOW, NOW, NOW);
        PaymentTransaction payment = PaymentTransaction.restore(5, 9, PaymentMethod.BANK_TRANSFER, "PAYOS", 901L, null,
            "FT1", null, new BigDecimal("180000.00"), PaymentStatus.REFUNDED, NOW, 0, NOW, NOW, List.of(given));
        assertThrows(InvalidStateTransitionException.class, () -> payment.completeRefund(4, NOW));
    }

    @Test
    void aManualRefundRecordedEarlierIsCompletedByHandAndNotReplacedByAVoucher() {
        var open = Refund.restore(3, new BigDecimal("180000.00"), "late", RefundMethod.MANUAL, null,
            RefundStatus.PENDING, null, NOW, NOW);
        PaymentTransaction payment = PaymentTransaction.restore(5, 9, PaymentMethod.BANK_TRANSFER, "PAYOS", 901L, null,
            "FT1", null, new BigDecimal("180000.00"), PaymentStatus.SUCCESS, NOW, 0, NOW, NOW, List.of(open));
        assertFalse(payment.compensateWithVoucher(77, "late", NOW));
        payment.completeRefund(3, NOW);
        assertEquals(PaymentStatus.REFUNDED, payment.status());
        assertEquals(RefundStatus.COMPLETED, payment.refunds().getFirst().status());
    }

    @Test
    void checkoutCanOnlyBeAttachedWhilePending() {
        PaymentTransaction payment = pending();
        payment.attachCheckout("link-1", "https://pay.payos.vn/web/link-1", NOW);
        assertEquals("https://pay.payos.vn/web/link-1", payment.checkoutUrl());
        payment.markFailed(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> payment.attachCheckout("x", "https://x", NOW));
    }

    private static PaymentTransaction pending() {
        PaymentTransaction payment = PaymentTransaction.initiate(9, new BigDecimal("180000.00"), 901L, NOW);
        return PaymentTransaction.restore(5, payment.bookingId(), payment.paymentMethod(), payment.provider(),
            payment.orderCode(), null, null, null, payment.amount(), payment.status(), null, 0, NOW, NOW, List.of());
    }
}
