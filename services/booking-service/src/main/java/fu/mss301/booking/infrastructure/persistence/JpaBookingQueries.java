package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.BookingSearch;
import fu.mss301.booking.application.booking.OccupiedSeat;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaBookingQueries implements BookingQueries {
    private final BookingJpaRepository jpa;

    JpaBookingQueries(BookingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BookingPage<Booking> findByUser(Integer userId, int page, int size) {
        return toPage(jpa.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
    }

    @Override
    public BookingPage<Booking> search(BookingSearch search, int page, int size) {
        // booking_time equals created_at; sorting by it lets MySQL read idx_bookings_booking_time directly.
        return toPage(jpa.findAll(matching(search),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "bookingTime", "id"))));
    }

    @Override
    public List<OccupiedSeat> findOccupiedSeats(Long showtimeId) {
        return jpa.findSeats(showtimeId, EnumSet.of(SeatHoldStatus.HELD, SeatHoldStatus.BOOKED));
    }

    private static Specification<BookingEntity> matching(BookingSearch search) {
        return (root, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            if (search.status() != null) conditions.add(builder.equal(root.get("status"), search.status()));
            if (search.showtimeId() != null) conditions.add(builder.equal(root.get("showtimeId"), search.showtimeId()));
            if (search.userId() != null) conditions.add(builder.equal(root.get("userId"), search.userId()));
            if (search.code() != null) conditions.add(builder.equal(root.get("bookingCode"), search.code()));
            if (search.from() != null) {
                conditions.add(builder.greaterThanOrEqualTo(root.<LocalDateTime>get("bookingTime"), search.from()));
            }
            if (search.to() != null) conditions.add(builder.lessThan(root.<LocalDateTime>get("bookingTime"), search.to()));
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static BookingPage<Booking> toPage(Page<BookingEntity> result) {
        return new BookingPage<>(result.getContent().stream().map(BookingMapper::toDomain).toList(), result.getNumber(),
            result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
