package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.Refund;
import fu.mss301.booking.domain.payment.RefundMethod;
import fu.mss301.booking.domain.payment.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundView(Integer id, Integer paymentId, Integer bookingId, Long orderCode, BigDecimal amount,
                         String reason, RefundMethod method, Integer voucherId, RefundStatus status,
                         LocalDateTime createdAt, LocalDateTime refundedAt) {
    public static RefundView of(PaymentTransaction payment, Refund refund) {
        return new RefundView(refund.id(), payment.id(), payment.bookingId(), payment.orderCode(),
            refund.amount(), refund.reason(), refund.method(), refund.voucherId(), refund.status(), refund.createdAt(),
            refund.refundedAt());
    }
}
