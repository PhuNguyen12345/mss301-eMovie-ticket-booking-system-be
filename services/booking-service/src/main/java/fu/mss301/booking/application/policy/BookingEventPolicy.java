package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.payment.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Booking released its seats -> close its open payment attempts and their PayOS links. */
@Component
public class BookingEventPolicy {
    private static final Logger log = LoggerFactory.getLogger(BookingEventPolicy.class);
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final TransactionRunner tx;
    private final Clock clock;

    public BookingEventPolicy(PaymentRepository payments, PaymentGateway gateway, TransactionRunner tx, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.tx = tx;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BookingExpired event) {
        closeOpenPayments(event.bookingId(), "Booking hold expired");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BookingCancelled event) {
        closeOpenPayments(event.bookingId(), "Booking cancelled by customer");
    }

    private void closeOpenPayments(Integer bookingId, String reason) {
        try {
            List<Long> openLinks = tx.call(() -> payments.findPendingByBookingIdForUpdate(bookingId).stream()
                .filter(payment -> payment.markFailed(LocalDateTime.now(clock)))
                .map(payment -> {
                    payments.save(payment);
                    return payment.getCheckoutUrl() == null ? null : payment.getOrderCode();
                })
                .filter(java.util.Objects::nonNull).toList());
            for (Long orderCode : openLinks) {
                try {
                    gateway.cancelLink(orderCode, reason);
                } catch (RuntimeException exception) {
                    // Best effort: an unpaid link also expires on PayOS' side at the booking's expiredAt.
                    log.warn("Could not cancel PayOS link {}: {}", orderCode, exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            log.error("Closing payments of booking {} failed", bookingId, exception);
        }
    }
}
