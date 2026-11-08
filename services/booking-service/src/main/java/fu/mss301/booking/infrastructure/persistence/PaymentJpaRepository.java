package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Integer>, JpaSpecificationExecutor<PaymentEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> lockById(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderCode = :orderCode")
    Optional<PaymentEntity> lockByOrderCode(@Param("orderCode") Long orderCode);

    @Query("select r.payment.id from RefundEntity r where r.id = :refundId")
    Optional<Integer> findPaymentIdByRefundId(@Param("refundId") Integer refundId);

    List<PaymentEntity> findByBookingIdOrderByIdAsc(Integer bookingId);

    List<PaymentEntity> findByBookingIdInOrderByIdAsc(Collection<Integer> bookingIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.bookingId = :bookingId and p.status = :status order by p.id")
    List<PaymentEntity> lockByBookingIdAndStatus(@Param("bookingId") Integer bookingId,
                                                      @Param("status") PaymentStatus status);

    long countByBookingId(Integer bookingId);

    @Query("""
        select p from PaymentEntity p, BookingEntity b
        where b.id = p.bookingId and p.status = :paymentStatus and b.status <> :confirmed
          and not exists (select r.id from RefundEntity r where r.payment = p)
        order by p.id
        """)
    List<PaymentEntity> findSuccessfulWithUnconfirmedBooking(@Param("paymentStatus") PaymentStatus paymentStatus,
                                                                  @Param("confirmed") BookingStatus confirmed,
                                                                  Pageable limit);

    @Query("select distinct p from PaymentEntity p join p.refunds r where r.status = :status order by p.id")
    List<PaymentEntity> findByRefundStatus(@Param("status") RefundStatus status);
}
