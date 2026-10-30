package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentTransaction, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.id = :id")
    Optional<PaymentTransaction> lockById(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.orderCode = :orderCode")
    Optional<PaymentTransaction> lockByOrderCode(@Param("orderCode") Long orderCode);

    @Query("select r.payment.id from Refund r where r.id = :refundId")
    Optional<Integer> findPaymentIdByRefundId(@Param("refundId") Integer refundId);

    List<PaymentTransaction> findByBookingIdOrderByIdAsc(Integer bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.bookingId = :bookingId and p.status = :status order by p.id")
    List<PaymentTransaction> lockByBookingIdAndStatus(@Param("bookingId") Integer bookingId,
                                                      @Param("status") PaymentStatus status);

    long countByBookingId(Integer bookingId);

    @Query("""
        select p from PaymentTransaction p, Booking b
        where b.id = p.bookingId and p.status = :paymentStatus and b.status <> :confirmed
          and not exists (select r.id from Refund r where r.payment = p)
        order by p.id
        """)
    List<PaymentTransaction> findSuccessfulWithUnconfirmedBooking(@Param("paymentStatus") PaymentStatus paymentStatus,
                                                                  @Param("confirmed") BookingStatus confirmed,
                                                                  Pageable limit);

    @Query("select distinct p from PaymentTransaction p join p.refunds r where r.status = :status order by p.id")
    List<PaymentTransaction> findByRefundStatus(@Param("status") RefundStatus status);
}
