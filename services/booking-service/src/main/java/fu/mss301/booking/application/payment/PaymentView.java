package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentView(Integer id, Integer bookingId, Long orderCode, String provider, BigDecimal amount,
                          PaymentStatus status, String checkoutUrl, LocalDateTime paidAt) {
    public static PaymentView of(PaymentTransaction payment) {
        return new PaymentView(payment.getId(), payment.getBookingId(), payment.getOrderCode(), payment.getProvider(),
            payment.getAmount(), payment.getStatus(), payment.getCheckoutUrl(), payment.getPaidAt());
    }
}
