package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.payment.PaymentSettlementService;
import fu.mss301.booking.domain.payment.event.PaymentSucceeded;

import java.lang.System.Logger.Level;

/** Payment aggregate changed -> update the Booking aggregate in a separate transaction. */
public class PaymentEventPolicy {
    private static final System.Logger log = System.getLogger(PaymentEventPolicy.class.getName());
    private final PaymentSettlementService settlement;

    public PaymentEventPolicy(PaymentSettlementService settlement) {
        this.settlement = settlement;
    }

    public void on(PaymentSucceeded event) {
        try {
            settlement.settle(event.paymentId(), event.bookingId());
        } catch (RuntimeException exception) {
            // The payment is already committed as SUCCESS; the reconciliation job will retry this step.
            log.log(Level.ERROR, () -> "Settling payment " + event.paymentId() + " for booking " + event.bookingId()
                + " failed", exception);
        }
    }
}
