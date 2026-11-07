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
    void refundOnlyForSuccessfulPaymentAndOnlyOnce() {
        PaymentTransaction payment = pending();
        assertThrows(InvalidStateTransitionException.class, () -> payment.requestRefund("late", NOW));
        payment.markSucceeded(new BigDecimal("180000"), "FT1", NOW, NOW);
        assertTrue(payment.requestRefund("late", NOW));
        assertFalse(payment.requestRefund("late again", NOW));
        assertEquals(1, payment.refunds().size());
        assertEquals(RefundStatus.PENDING, payment.refunds().getFirst().status());
        assertEquals(new BigDecimal("180000.00"), payment.refunds().getFirst().amount());
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
