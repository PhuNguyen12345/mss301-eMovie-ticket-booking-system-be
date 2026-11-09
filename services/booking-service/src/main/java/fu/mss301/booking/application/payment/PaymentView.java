package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param status a {@link PaymentStatus}, or {@value #NOT_REQUIRED} when vouchers cover the whole booking and it was
 *               confirmed without a payment
 */
public record PaymentView(Integer id, Integer bookingId, Long orderCode, String provider, BigDecimal amount,
                          String status, String checkoutUrl, LocalDateTime paidAt) {
    public static final String NOT_REQUIRED = "NOT_REQUIRED";

    public static PaymentView of(PaymentTransaction payment) {
        return new PaymentView(payment.id(), payment.bookingId(), payment.orderCode(), payment.provider(),
            payment.amount(), payment.status().name(), payment.checkoutUrl(), payment.paidAt());
    }

    /** Nothing to pay: no payment row and no PayOS link exist. */
    public static PaymentView notRequired(Integer bookingId) {
        return new PaymentView(null, bookingId, null, null, BigDecimal.ZERO.setScale(2), NOT_REQUIRED, null, null);
    }
}
