package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.OccupiedSeat;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Repository
class JpaBookingRepository implements BookingRepository {
    private final BookingJpaRepository jpa;

    JpaBookingRepository(BookingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Booking save(Booking booking) {
        // Spring Data publishes the aggregate's registered events on save().
        return jpa.save(booking);
    }

    @Override
    public Optional<Booking> findById(Integer id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Booking> findByIdForUpdate(Integer id) {
        return jpa.lockById(id);
    }

    @Override
    public Page<Booking> findByUserId(Integer userId, Pageable pageable) {
        return jpa.findByUserId(userId, pageable);
    }

    @Override
    public List<Integer> findOverdueIds(LocalDateTime now, int limit) {
        return jpa.findIdsByStatusExpiredBefore(BookingStatus.PENDING_PAYMENT, now, PageRequest.of(0, limit));
    }

    @Override
    public List<OccupiedSeat> findOccupiedSeats(Long showtimeId) {
        return jpa.findSeats(showtimeId, EnumSet.of(SeatHoldStatus.HELD, SeatHoldStatus.BOOKED));
    }
}
