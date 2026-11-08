package fu.mss301.booking.domain.booking;

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

    Optional<Booking> findByCode(String bookingCode);

    Optional<Booking> findByCodeForUpdate(String bookingCode);

    List<Integer> findOverdueIds(LocalDateTime now, int limit);

    /** Bookings of a showtime that are waiting for payment or paid. */
    List<Integer> findOpenIdsByShowtime(Long showtimeId);

    /** Paid bookings whose showtime was cancelled and whose customer is not compensated yet, oldest first. */
    List<Integer> findUncompensatedIds(int limit);
}
