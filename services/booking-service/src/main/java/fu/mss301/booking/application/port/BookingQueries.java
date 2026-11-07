package fu.mss301.booking.application.port;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.OccupiedSeat;
import fu.mss301.booking.domain.booking.Booking;

import java.util.List;

/** Read-only queries that are not aggregate persistence. */
public interface BookingQueries {
    /** A user's bookings, newest first. */
    BookingPage<Booking> findByUser(Integer userId, int page, int size);

    /** Seats currently HELD or BOOKED on a showtime. */
    List<OccupiedSeat> findOccupiedSeats(Long showtimeId);
}
