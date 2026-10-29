package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Applies a provider-confirmed payment, then brings the booking in line with it. Each step touches one
 * aggregate in its own transaction; the booking step is driven by the PaymentSucceeded event.
 */
@Service
public class PaymentSettlementService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final TransactionRunner tx;
    private final Clock clock;

    public PaymentSettlementService(PaymentRepository payments, BookingRepository bookings, TransactionRunner tx,
                                    Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.tx = tx;
        this.clock = clock;
    }

    /** @return false when no payment has this order code (e.g. PayOS' webhook URL test call) */
    public boolean recordSuccess(long orderCode, long paidAmount, String reference, LocalDateTime paidAt) {
        return tx.call(() -> payments.findByOrderCodeForUpdate(orderCode).map(payment -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (payment.markSucceeded(BigDecimal.valueOf(paidAmount), reference, paidAt == null ? now : paidAt, now)) {
                payments.save(payment); // publishes PaymentSucceeded after commit
            }
            return true;
        }).orElse(false));
    }

    /**
     * Confirms the booking for a successful payment. If the booking can no longer be confirmed (expired,
     * cancelled, or already paid by another attempt) the payment is flagged for refund instead.
     */
    public void settle(Integer paymentId, Integer bookingId) {
        boolean keep = tx.call(() -> {
            var booking = bookings.findByIdForUpdate(bookingId).orElseThrow(
                () -> new IllegalStateException("Payment " + paymentId + " references missing booking " + bookingId));
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.confirm(LocalDateTime.now(clock));
                bookings.save(booking);
                return true;
            }
            return booking.getStatus() == BookingStatus.CONFIRMED && isFirstSuccessfulPayment(paymentId, bookingId);
        });
        if (keep) return;
        tx.run(() -> payments.findByIdForUpdate(paymentId).ifPresent(payment -> {
            if (payment.requestRefund("Booking " + bookingId + " could not be confirmed with this payment",
                    LocalDateTime.now(clock))) {
                payments.save(payment);
            }
        }));
    }

    private boolean isFirstSuccessfulPayment(Integer paymentId, Integer bookingId) {
        return payments.findByBookingId(bookingId).stream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED)
            .min(Comparator.comparing(PaymentTransaction::getId))
            .map(p -> p.getId().equals(paymentId)).orElse(true);
    }
}
