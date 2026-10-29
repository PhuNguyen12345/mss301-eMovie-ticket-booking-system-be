package fu.mss301.booking.domain.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence contract for the {@link Booking} aggregate; seats are always saved through the root. */
public interface BookingRepository {
    /** Persists the whole aggregate and publishes its registered domain events. */
    Booking save(Booking booking);

    Optional<Booking> findById(Integer id);

    /** Loads the aggregate with a row lock so concurrent confirm/expire/cancel are serialized. */
    Optional<Booking> findByIdForUpdate(Integer id);

    Page<Booking> findByUserId(Integer userId, Pageable pageable);

    List<Integer> findOverdueIds(LocalDateTime now, int limit);

    List<OccupiedSeat> findOccupiedSeats(Long showtimeId);
}
