package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.exception.RefundNotFoundException;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.RefundStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Refunds only cover system-side cases, such as money arriving after a booking closed. Customers who bought a
 * ticket are not refunded. Staff transfer the money back manually and record it here.
 */
public class RefundAdminUseCase {
    private final PaymentRepository payments;
    private final Transactions transactions;
    private final Clock clock;

    public RefundAdminUseCase(PaymentRepository payments, Transactions transactions, Clock clock) {
        this.payments = payments;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<RefundView> list(RefundStatus status) {
        return transactions.read(() -> payments.findWithRefundStatus(status).stream()
            .flatMap(payment -> payment.refunds().stream()
                .filter(refund -> refund.status() == status)
                .map(refund -> RefundView.of(payment, refund)))
            .toList());
    }

    public RefundView complete(Integer refundId) {
        return transactions.write(() -> {
            var payment = payments.findByRefundIdForUpdate(refundId)
                .orElseThrow(RefundNotFoundException::new);
            payment.completeRefund(refundId, LocalDateTime.now(clock));
            var saved = payments.save(payment);
            return saved.refunds().stream().filter(r -> r.id().equals(refundId))
                .map(r -> RefundView.of(saved, r)).findFirst().orElseThrow();
        });
    }
}
