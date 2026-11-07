package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.payment.PaymentRepository;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Booking released its seats -> close its open payment attempts and their PayOS links. */
public class BookingEventPolicy {
    private static final System.Logger log = System.getLogger(BookingEventPolicy.class.getName());
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final Transactions transactions;
    private final Clock clock;

    public BookingEventPolicy(PaymentRepository payments, PaymentGateway gateway, Transactions transactions, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void on(BookingExpired event) {
        closeOpenPayments(event.bookingId(), "Booking hold expired");
    }

    public void on(BookingCancelled event) {
        closeOpenPayments(event.bookingId(), "Booking cancelled by customer");
    }

    private void closeOpenPayments(Integer bookingId, String reason) {
        try {
            List<Long> openLinks = transactions.write(() -> payments.findPendingByBookingIdForUpdate(bookingId).stream()
                .filter(payment -> payment.markFailed(LocalDateTime.now(clock)))
                .map(payment -> {
                    payments.save(payment);
                    return payment.checkoutUrl() == null ? null : payment.orderCode();
                })
                .filter(java.util.Objects::nonNull).toList());
            openLinks.forEach(orderCode -> cancelIfStillOpen(orderCode, reason));
        } catch (RuntimeException exception) {
            log.log(Level.ERROR, () -> "Closing payments of booking " + bookingId + " failed", exception);
        }
    }

    /**
     * PayOS closes a link by itself at the booking's expiredAt and rejects cancelling a closed link (code 221),
     * so only a link that is still open is cancelled. Best effort: a failure here leaves a link that expires.
     */
    private void cancelIfStillOpen(Long orderCode, String reason) {
        try {
            if (gateway.getLinkState(orderCode).status() == LinkStatus.PENDING) {
                gateway.cancelLink(orderCode, reason);
            }
        } catch (RuntimeException exception) {
            log.log(Level.WARNING, () -> "Could not cancel PayOS link " + orderCode + ": " + exception.getMessage());
        }
    }
}
