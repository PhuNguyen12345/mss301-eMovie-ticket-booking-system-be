package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatAlreadyTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaBookingRepository implements BookingRepository {
    private final BookingJpaRepository jpa;
    private final ComboJpaRepository combos;

    JpaBookingRepository(BookingJpaRepository jpa, ComboJpaRepository combos) {
        this.jpa = jpa;
        this.combos = combos;
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity;
        if (booking.id() == null) {
            entity = BookingMapper.newEntity(booking);
        } else {
            entity = jpa.findById(booking.id()).orElseThrow();
            if (!Objects.equals(entity.getVersion(), booking.version())) {
                throw new ObjectOptimisticLockingFailureException(BookingEntity.class, booking.id());
            }
            BookingMapper.update(booking, entity, combos::getReferenceById);
        }
        try {
            return BookingMapper.toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            // uq_booking_seats_active: another booking holds or owns one of these seats.
            throw new SeatAlreadyTakenException();
        }
    }

    @Override
    public Optional<Booking> findById(Integer id) {
        return jpa.findById(id).map(BookingMapper::toDomain);
    }

    @Override
    public Optional<Booking> findByIdForUpdate(Integer id) {
        return jpa.lockById(id).map(BookingMapper::toDomain);
    }

    @Override
    public Optional<Booking> findByCode(String bookingCode) {
        return jpa.findByBookingCode(bookingCode).map(BookingMapper::toDomain);
    }

    @Override
    public Optional<Booking> findByCodeForUpdate(String bookingCode) {
        return jpa.lockByBookingCode(bookingCode).map(BookingMapper::toDomain);
    }

    @Override
    public List<Integer> findOpenIdsByShowtime(Long showtimeId) {
        return jpa.findIdsByShowtimeAndStatusIn(showtimeId,
            List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));
    }

    @Override
    public List<Integer> findUncompensatedIds(int limit) {
        return jpa.findIdsByStatusNotCompensated(BookingStatus.SHOWTIME_CANCELLED, PageRequest.of(0, limit));
    }

    @Override
    public List<Integer> findOverdueIds(LocalDateTime now, int limit) {
        return jpa.findIdsByStatusExpiredBefore(BookingStatus.PENDING_PAYMENT, now, PageRequest.of(0, limit));
    }
}
