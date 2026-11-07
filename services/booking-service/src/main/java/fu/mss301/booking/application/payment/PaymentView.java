package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentView(Integer id, Integer bookingId, Long orderCode, String provider, BigDecimal amount,
                          PaymentStatus status, String checkoutUrl, LocalDateTime paidAt) {
    public static PaymentView of(PaymentTransaction payment) {
        return new PaymentView(payment.id(), payment.bookingId(), payment.orderCode(), payment.provider(),
            payment.amount(), payment.status(), payment.checkoutUrl(), payment.paidAt());
    }
}
