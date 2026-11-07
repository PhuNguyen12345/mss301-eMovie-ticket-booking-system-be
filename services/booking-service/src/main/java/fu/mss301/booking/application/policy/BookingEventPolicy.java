package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.port.PaymentGateway;
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
            for (Long orderCode : openLinks) {
                try {
                    gateway.cancelLink(orderCode, reason);
                } catch (RuntimeException exception) {
                    // Best effort: an unpaid link also expires on PayOS' side at the booking's expiredAt.
                    log.log(Level.WARNING, () -> "Could not cancel PayOS link " + orderCode + ": " + exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            log.log(Level.ERROR, () -> "Closing payments of booking " + bookingId + " failed", exception);
        }
    }
}
