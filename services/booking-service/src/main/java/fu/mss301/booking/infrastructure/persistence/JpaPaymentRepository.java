package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.RefundStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaPaymentRepository implements PaymentRepository {
    private final PaymentJpaRepository jpa;

    JpaPaymentRepository(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction payment) {
        return jpa.save(payment);
    }

    @Override
    public Optional<PaymentTransaction> findByIdForUpdate(Integer id) {
        return jpa.lockById(id);
    }

    @Override
    public Optional<PaymentTransaction> findByOrderCodeForUpdate(Long orderCode) {
        return jpa.lockByOrderCode(orderCode);
    }

    @Override
    public Optional<PaymentTransaction> findByRefundIdForUpdate(Integer refundId) {
        return jpa.findPaymentIdByRefundId(refundId).flatMap(jpa::lockById);
    }

    @Override
    public List<PaymentTransaction> findByBookingId(Integer bookingId) {
        return jpa.findByBookingIdOrderByIdAsc(bookingId);
    }

    @Override
    public List<PaymentTransaction> findPendingByBookingIdForUpdate(Integer bookingId) {
        return jpa.lockByBookingIdAndStatus(bookingId, PaymentStatus.PENDING);
    }

    @Override
    public long countByBookingId(Integer bookingId) {
        return jpa.countByBookingId(bookingId);
    }

    @Override
    public List<PaymentTransaction> findUnsettledSuccessful(int limit) {
        return jpa.findSuccessfulWithUnconfirmedBooking(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED,
            PageRequest.of(0, limit));
    }

    @Override
    public List<PaymentTransaction> findWithRefundStatus(RefundStatus status) {
        return jpa.findByRefundStatus(status);
    }
}
