package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.Refund;
import fu.mss301.booking.domain.payment.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundView(Integer id, Integer paymentId, Integer bookingId, Long orderCode, BigDecimal amount,
                         String reason, RefundStatus status, LocalDateTime createdAt, LocalDateTime refundedAt) {
    public static RefundView of(PaymentTransaction payment, Refund refund) {
        return new RefundView(refund.getId(), payment.getId(), payment.getBookingId(), payment.getOrderCode(),
            refund.getAmount(), refund.getReason(), refund.getStatus(), refund.getCreatedAt(), refund.getRefundedAt());
    }
}
