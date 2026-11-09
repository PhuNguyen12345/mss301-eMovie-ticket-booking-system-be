package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.MovieCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.TicketDetails;

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills in the ticket of bookings made before the ticket, or its movie and cinema ids, were stored, so statistics
 * can count them. A booking that already shows ticket values keeps them; only the ids are added.
 */
public class TicketBackfillService {
    private static final System.Logger log = System.getLogger(TicketBackfillService.class.getName());
    private final BookingRepository bookings;
    private final ShowtimeCatalog showtimes;
    private final MovieCatalog movies;
    private final CinemaCatalog cinemas;
    private final Transactions transactions;

    public TicketBackfillService(BookingRepository bookings, ShowtimeCatalog showtimes, MovieCatalog movies,
                                 CinemaCatalog cinemas, Transactions transactions) {
        this.bookings = bookings;
        this.showtimes = showtimes;
        this.movies = movies;
        this.cinemas = cinemas;
        this.transactions = transactions;
    }

    /**
     * Walks every booking still missing the ids, a page at a time. One that cannot be filled, for example because
     * a service is down, is skipped and tried again on the next run.
     *
     * @return how many bookings were filled in
     */
    public int fillMissing(int batchSize) {
        Map<Long, TicketDetails> tickets = new HashMap<>();
        int filled = 0;
        int afterId = 0;
        while (true) {
            int cursor = afterId;
            List<Integer> ids = transactions.read(() -> bookings.findIdsMissingCatalogIds(cursor, batchSize));
            if (ids.isEmpty()) break;
            for (Integer bookingId : ids) {
                try {
                    if (fill(bookingId, tickets)) filled++;
                } catch (RuntimeException exception) {
                    log.log(Level.WARNING, () -> "Could not fill in the ticket of booking " + bookingId + ": "
                        + exception.getMessage());
                }
            }
            afterId = ids.getLast();
        }
        if (filled > 0) log.log(Level.INFO, "Filled in the ticket of {0} bookings", filled);
        return filled;
    }

    private boolean fill(Integer bookingId, Map<Long, TicketDetails> tickets) {
        Long showtimeId = transactions.read(() -> bookings.findById(bookingId)).map(Booking::showtimeId).orElse(null);
        if (showtimeId == null) return false;
        // Remote reads happen before the transaction, and once per showtime in a run.
        TicketDetails current = tickets.get(showtimeId);
        if (current == null) {
            current = ticketOf(showtimes.getShowtime(showtimeId));
            tickets.put(showtimeId, current);
        }
        TicketDetails ticket = current;
        return transactions.write(() -> bookings.findByIdForUpdate(bookingId)
            .filter(locked -> locked.completeTicket(ticket))
            .map(bookings::save)
            .isPresent());
    }

    private TicketDetails ticketOf(ShowtimeSnapshot showtime) {
        var movie = movies.getMovie(showtime.movieId());
        return new TicketDetails(movie.title(), movie.posterUrl(), cinemas.cinemaName(showtime.cinemaId()),
            cinemas.screenName(showtime.screenId()), showtime.startTime(), showtime.endTime(), showtime.movieId(),
            showtime.cinemaId());
    }
}
