package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.Refund;
import fu.mss301.booking.domain.payment.RefundMethod;
import fu.mss301.booking.domain.payment.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * One payment as admins see it in the history, with every refund recorded on it. The checkout link is left out:
 * it is for the customer only.
 *
 * @param providerTransactionId the bank reference PayOS reports once the money arrived, or null
 */
public record PaymentHistoryView(Integer id, Integer bookingId, String bookingCode, Integer userId, Long orderCode,
                                 String provider, BigDecimal amount, PaymentStatus status,
                                 String providerTransactionId, LocalDateTime paidAt, LocalDateTime createdAt,
                                 List<RefundLine> refunds) {
    public static PaymentHistoryView of(PaymentTransaction payment, String bookingCode, Integer userId) {
        return new PaymentHistoryView(payment.id(), payment.bookingId(), bookingCode, userId, payment.orderCode(),
            payment.provider(), payment.amount(), payment.status(), payment.providerTransactionId(), payment.paidAt(),
            payment.createdAt(), payment.refunds().stream().sorted(Comparator.comparing(Refund::id))
                .map(RefundLine::of).toList());
    }

    /** @param voucherId the voucher the money went into, for {@link RefundMethod#VOUCHER} */
    public record RefundLine(Integer id, BigDecimal amount, RefundMethod method, Integer voucherId,
                             RefundStatus status, String reason, LocalDateTime createdAt, LocalDateTime refundedAt) {
        static RefundLine of(Refund refund) {
            return new RefundLine(refund.id(), refund.amount(), refund.method(), refund.voucherId(), refund.status(),
                refund.reason(), refund.createdAt(), refund.refundedAt());
        }
    }
}
