package fu.mss301.booking.domain.payment;

import java.util.List;
import java.util.Optional;

/** Persistence contract for the {@link PaymentTransaction} aggregate; refunds are saved through the root. */
public interface PaymentRepository {
    /** Persists the whole aggregate and publishes its registered domain events. */
    PaymentTransaction save(PaymentTransaction payment);

    Optional<PaymentTransaction> findByIdForUpdate(Integer id);

    Optional<PaymentTransaction> findByOrderCodeForUpdate(Long orderCode);

    Optional<PaymentTransaction> findByRefundIdForUpdate(Integer refundId);

    List<PaymentTransaction> findByBookingId(Integer bookingId);

    List<PaymentTransaction> findPendingByBookingIdForUpdate(Integer bookingId);

    long countByBookingId(Integer bookingId);

    /** Successful payments whose booking was not confirmed and that have no refund yet (lost-event safety net). */
    List<PaymentTransaction> findUnsettledSuccessful(int limit);

    List<PaymentTransaction> findWithRefundStatus(RefundStatus status);
}
