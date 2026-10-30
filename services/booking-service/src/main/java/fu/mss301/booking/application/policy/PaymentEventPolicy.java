package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.payment.PaymentSettlementService;
import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Payment aggregate changed -> update the Booking aggregate in a separate transaction. */
@Component
public class PaymentEventPolicy {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPolicy.class);
    private final PaymentSettlementService settlement;

    public PaymentEventPolicy(PaymentSettlementService settlement) {
        this.settlement = settlement;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentSucceeded event) {
        try {
            settlement.settle(event.paymentId(), event.bookingId());
        } catch (RuntimeException exception) {
            // The payment is already committed as SUCCESS; the reconciliation job will retry this step.
            log.error("Settling payment {} for booking {} failed", event.paymentId(), event.bookingId(), exception);
        }
    }
}
