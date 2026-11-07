package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.RefundStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaPaymentRepository implements PaymentRepository {
    private final PaymentJpaRepository jpa;

    JpaPaymentRepository(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction payment) {
        PaymentEntity entity;
        if (payment.id() == null) {
            entity = PaymentMapper.newEntity(payment);
        } else {
            entity = jpa.findById(payment.id()).orElseThrow();
            if (!Objects.equals(entity.getVersion(), payment.version())) {
                throw new ObjectOptimisticLockingFailureException(PaymentEntity.class, payment.id());
            }
            PaymentMapper.update(payment, entity);
        }
        return PaymentMapper.toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<PaymentTransaction> findByIdForUpdate(Integer id) {
        return jpa.lockById(id).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByOrderCodeForUpdate(Long orderCode) {
        return jpa.lockByOrderCode(orderCode).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByRefundIdForUpdate(Integer refundId) {
        return jpa.findPaymentIdByRefundId(refundId).flatMap(jpa::lockById).map(PaymentMapper::toDomain);
    }

    @Override
    public List<PaymentTransaction> findByBookingId(Integer bookingId) {
        return toDomain(jpa.findByBookingIdOrderByIdAsc(bookingId));
    }

    @Override
    public List<PaymentTransaction> findByBookingIds(Collection<Integer> bookingIds) {
        return toDomain(jpa.findByBookingIdInOrderByIdAsc(bookingIds));
    }

    @Override
    public List<PaymentTransaction> findPendingByBookingIdForUpdate(Integer bookingId) {
        return toDomain(jpa.lockByBookingIdAndStatus(bookingId, PaymentStatus.PENDING));
    }

    @Override
    public long countByBookingId(Integer bookingId) {
        return jpa.countByBookingId(bookingId);
    }

    @Override
    public List<PaymentTransaction> findUnsettledSuccessful(int limit) {
        return toDomain(jpa.findSuccessfulWithUnconfirmedBooking(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED,
            PageRequest.of(0, limit)));
    }

    @Override
    public List<PaymentTransaction> findWithRefundStatus(RefundStatus status) {
        return toDomain(jpa.findByRefundStatus(status));
    }

    private static List<PaymentTransaction> toDomain(List<PaymentEntity> entities) {
        return entities.stream().map(PaymentMapper::toDomain).toList();
    }
}
