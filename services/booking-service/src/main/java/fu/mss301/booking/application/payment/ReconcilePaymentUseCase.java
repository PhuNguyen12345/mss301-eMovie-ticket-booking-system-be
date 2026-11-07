package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.lang.System.Logger.Level;

/** Pulls payment state from PayOS so the system does not depend on webhooks alone. */
public class ReconcilePaymentUseCase {
    private static final System.Logger log = System.getLogger(ReconcilePaymentUseCase.class.getName());
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;
    private final BookingQueryService bookingQueries;
    private final Transactions transactions;

    public ReconcilePaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                   PaymentSettlementService settlement, BookingQueryService bookingQueries,
                                   Transactions transactions) {
        this.payments = payments;
        this.gateway = gateway;
        this.settlement = settlement;
        this.bookingQueries = bookingQueries;
        this.transactions = transactions;
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
        // Snapshot first: PayOS is called outside any transaction.
        for (PaymentTransaction payment : transactions.read(() -> payments.findByBookingId(bookingId))) {
            if (!payment.isPending() || payment.checkoutUrl() == null) continue;
            try {
                var state = gateway.getLinkState(payment.orderCode());
                if (state.status() == LinkStatus.PAID) {
                    paid |= settlement.recordSuccess(payment.orderCode(), state.amountPaid(), state.reference(),
                        state.paidAt());
                }
            } catch (PaymentGatewayException exception) {
                log.log(Level.WARNING, () -> "Could not reconcile order " + payment.orderCode() + " with PayOS: "
                    + exception.getMessage());
            }
        }
        return paid;
    }

    /** Safety net for PaymentSucceeded events lost between commit and listener (e.g. a crash). */
    public void settleUnsettled(int limit) {
        for (PaymentTransaction payment : transactions.read(() -> payments.findUnsettledSuccessful(limit))) {
            try {
                settlement.settle(payment.id(), payment.bookingId());
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, () -> "Could not settle payment " + payment.id() + ": " + exception.getMessage());
            }
        }
    }
}
