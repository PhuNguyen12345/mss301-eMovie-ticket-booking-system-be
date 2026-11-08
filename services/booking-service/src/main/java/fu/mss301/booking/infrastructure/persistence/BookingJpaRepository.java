package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.booking.OccupiedSeat;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface BookingJpaRepository extends JpaRepository<BookingEntity, Integer>, JpaSpecificationExecutor<BookingEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> lockById(@Param("id") Integer id);

    Optional<BookingEntity> findByBookingCode(String bookingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.bookingCode = :code")
    Optional<BookingEntity> lockByBookingCode(@Param("code") String bookingCode);

    @Query("select b.id from BookingEntity b where b.showtimeId = :showtimeId and b.status in :statuses order by b.id")
    List<Integer> findIdsByShowtimeAndStatusIn(@Param("showtimeId") Long showtimeId,
                                               @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
        select b.id from BookingEntity b where b.status = :status and b.compensatedAt is null
        order by b.showtimeCancelledAt
        """)
    List<Integer> findIdsByStatusNotCompensated(@Param("status") BookingStatus status, Pageable limit);

    @Query("""
        select b.id from BookingEntity b where b.id > :afterId and (b.movieId is null or b.cinemaId is null)
        order by b.id
        """)
    List<Integer> findIdsMissingCatalogIds(@Param("afterId") Integer afterId, Pageable limit);

    Page<BookingEntity> findByUserId(Integer userId, Pageable pageable);

    @Query("select b.id from BookingEntity b where b.status = :status and b.expiredAt <= :now order by b.expiredAt")
    List<Integer> findIdsByStatusExpiredBefore(@Param("status") BookingStatus status, @Param("now") LocalDateTime now,
                                               Pageable limit);

    @Query("""
        select new fu.mss301.booking.application.booking.OccupiedSeat(s.seatId, s.status)
        from BookingSeatEntity s where s.showtimeId = :showtimeId and s.status in :statuses
        """)
    List<OccupiedSeat> findSeats(@Param("showtimeId") Long showtimeId,
                                 @Param("statuses") Collection<SeatHoldStatus> statuses);
}
