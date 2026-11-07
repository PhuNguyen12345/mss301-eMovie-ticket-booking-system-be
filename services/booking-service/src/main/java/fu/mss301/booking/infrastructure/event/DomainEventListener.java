package fu.mss301.booking.infrastructure.event;

import fu.mss301.booking.application.policy.BookingEventPolicy;
import fu.mss301.booking.application.policy.PaymentEventPolicy;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs the application policies once the transaction that raised an event has committed. */
@Component
class DomainEventListener {
    private final BookingEventPolicy bookingPolicy;
    private final PaymentEventPolicy paymentPolicy;

    DomainEventListener(BookingEventPolicy bookingPolicy, PaymentEventPolicy paymentPolicy) {
        this.bookingPolicy = bookingPolicy;
        this.paymentPolicy = paymentPolicy;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BookingExpired event) {
        bookingPolicy.on(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BookingCancelled event) {
        bookingPolicy.on(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(PaymentSucceeded event) {
        paymentPolicy.on(event);
    }
}
