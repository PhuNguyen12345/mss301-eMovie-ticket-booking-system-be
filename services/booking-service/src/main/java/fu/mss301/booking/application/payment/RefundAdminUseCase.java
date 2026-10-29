package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.RefundStatus;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** PayOS has no refund API: staff transfer the money back manually and record it here. */
@Service
public class RefundAdminUseCase {
    private final PaymentRepository payments;
    private final TransactionRunner tx;
    private final Clock clock;

    public RefundAdminUseCase(PaymentRepository payments, TransactionRunner tx, Clock clock) {
        this.payments = payments;
        this.tx = tx;
        this.clock = clock;
    }

    public List<RefundView> list(RefundStatus status) {
        return tx.call(() -> payments.findWithRefundStatus(status).stream()
            .flatMap(payment -> payment.getRefunds().stream()
                .filter(refund -> refund.getStatus() == status)
                .map(refund -> RefundView.of(payment, refund)))
            .toList());
    }

    public RefundView complete(Integer refundId) {
        return tx.call(() -> {
            var payment = payments.findByRefundIdForUpdate(refundId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Refund not found"));
            payment.completeRefund(refundId, LocalDateTime.now(clock));
            var saved = payments.save(payment);
            return saved.getRefunds().stream().filter(r -> r.getId().equals(refundId))
                .map(r -> RefundView.of(saved, r)).findFirst().orElseThrow();
        });
    }
}
