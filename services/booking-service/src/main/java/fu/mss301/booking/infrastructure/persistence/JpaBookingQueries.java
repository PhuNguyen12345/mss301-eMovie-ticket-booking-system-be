package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.OccupiedSeat;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.List;

@Repository
class JpaBookingQueries implements BookingQueries {
    private final BookingJpaRepository jpa;

    JpaBookingQueries(BookingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BookingPage<Booking> findByUser(Integer userId, int page, int size) {
        var result = jpa.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new BookingPage<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
            result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    public List<OccupiedSeat> findOccupiedSeats(Long showtimeId) {
        return jpa.findSeats(showtimeId, EnumSet.of(SeatHoldStatus.HELD, SeatHoldStatus.BOOKED));
    }
}
