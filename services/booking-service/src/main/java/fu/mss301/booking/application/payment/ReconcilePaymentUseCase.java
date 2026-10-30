package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Pulls payment state from PayOS so the system does not depend on webhooks alone. */
@Service
public class ReconcilePaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReconcilePaymentUseCase.class);
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;
    private final BookingQueryService bookingQueries;

    public ReconcilePaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                   PaymentSettlementService settlement, BookingQueryService bookingQueries) {
        this.payments = payments;
        this.gateway = gateway;
        this.settlement = settlement;
        this.bookingQueries = bookingQueries;
    }

    /** Customer came back from the PayOS page: refresh from PayOS, then return the booking. */
    public BookingView sync(Integer userId, Integer bookingId) {
        bookingQueries.getBooking(userId, false, bookingId); // ownership check, 404 otherwise
        reconcileBooking(bookingId);
        return bookingQueries.getBooking(userId, false, bookingId);
    }

    /** @return true when one of the booking's open payments turned out to be paid */
    public boolean reconcileBooking(Integer bookingId) {
        boolean paid = false;
        for (PaymentTransaction payment : payments.findByBookingId(bookingId)) {
            if (!payment.isPending() || payment.getCheckoutUrl() == null) continue;
            try {
                var state = gateway.getLinkState(payment.getOrderCode());
                if (state.status() == LinkStatus.PAID) {
                    paid |= settlement.recordSuccess(payment.getOrderCode(), state.amountPaid(), state.reference(),
                        state.paidAt());
                }
            } catch (PaymentGatewayException exception) {
                log.warn("Could not reconcile order {} with PayOS: {}", payment.getOrderCode(), exception.getMessage());
            }
        }
        return paid;
    }

    /** Safety net for PaymentSucceeded events lost between commit and listener (e.g. a crash). */
    public void settleUnsettled(int limit) {
        for (PaymentTransaction payment : payments.findUnsettledSuccessful(limit)) {
            try {
                settlement.settle(payment.getId(), payment.getBookingId());
            } catch (RuntimeException exception) {
                log.warn("Could not settle payment {}: {}", payment.getId(), exception.getMessage());
            }
        }
    }
}
