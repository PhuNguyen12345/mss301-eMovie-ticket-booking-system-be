package fu.mss301.showtime.application.usecase;

import fu.mss301.showtime.application.dto.*;
import fu.mss301.showtime.application.exception.ShowtimeNotFoundException;
import fu.mss301.showtime.application.port.*;
import fu.mss301.showtime.domain.model.*;
import fu.mss301.showtime.domain.repository.ShowtimeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ShowtimeServiceImpl implements ShowtimeService {
    private final ShowtimeRepository showtimes;
    private final ShowtimeQueries queries;
    private final ScreenSchedule screens;
    private final MovieCatalog movies;
    private final CinemaCatalog cinemas;
    private final BookingNotifier bookings;
    private final Transactions transactions;
    private final Clock clock;

    public ShowtimeServiceImpl(ShowtimeRepository showtimes, ShowtimeQueries queries, ScreenSchedule screens,
            MovieCatalog movies, CinemaCatalog cinemas, BookingNotifier bookings, Transactions transactions,
            Clock clock) {
        this.showtimes = showtimes;
        this.queries = queries;
        this.screens = screens;
        this.movies = movies;
        this.cinemas = cinemas;
        this.bookings = bookings;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public ShowtimePage getShowtimes(Integer movieId, Integer cinemaId, Integer screenId, LocalDate date,
            String location, ShowtimeStatus status, int page, int size) {
        var search = new ShowtimeSearch(movieId, cinemaId, screenId, date, location, status, page, size);
        return transactions.read(() -> queries.search(search));
    }

    @Override
    public ShowtimeView getShowtimeById(Long id) {
        Ids.requirePositive(id, "Showtime");
        return transactions.read(() -> ShowtimeView.from(showtimes.findById(id)
            .orElseThrow(() -> new ShowtimeNotFoundException(id))));
    }

    @Override
    public ShowtimeView createShowtime(ShowtimeCommand command) {
        var schedule = resolveSchedule(command);
        return transactions.write(() -> {
            screens.lock(schedule.screenId());
            var showtime = Showtime.schedule(schedule, LocalDateTime.now(clock));
            schedule.requireAvailable(screens.overlaps(schedule, null));
            return ShowtimeView.from(showtimes.save(showtime));
        });
    }

    @Override
    public ShowtimeView updateShowtime(Long id, ShowtimeCommand command) {
        Ids.requirePositive(id, "Showtime");
        var replacement = resolveSchedule(command);
        return transactions.write(() -> {
            var showtime = loadForUpdate(id);
            showtime.requireEditable(LocalDateTime.now(clock));
            int oldScreen = showtime.schedule().screenId();
            int newScreen = replacement.screenId();
            screens.lock(Math.min(oldScreen, newScreen));
            if (oldScreen != newScreen) screens.lock(Math.max(oldScreen, newScreen));
            showtime.reschedule(replacement, LocalDateTime.now(clock));
            replacement.requireAvailable(screens.overlaps(replacement, id));
            return ShowtimeView.from(showtimes.save(showtime));
        });
    }

    @Override
    public ShowtimeView cancelShowtime(Long id) {
        Ids.requirePositive(id, "Showtime");
        var cancelled = transactions.write(() -> {
            var showtime = loadForUpdate(id);
            screens.lock(showtime.schedule().screenId());
            showtime.cancel(LocalDateTime.now(clock));
            return ShowtimeView.from(showtimes.save(showtime));
        });
        // After the commit, so booking-service sees the showtime as cancelled. Repeating a cancellation notifies
        // again, which booking-service handles without compensating twice.
        bookings.showtimeCancelled(id);
        return cancelled;
    }

    private Showtime loadForUpdate(Long id) {
        return showtimes.findByIdForUpdate(id).orElseThrow(() -> new ShowtimeNotFoundException(id));
    }

    private Schedule resolveSchedule(ShowtimeCommand command) {
        if (command == null) throw new IllegalArgumentException("Showtime request is required");
        Ids.requirePositive(command.movieId(), "Movie");
        Ids.requirePositive(command.cinemaId(), "Cinema");
        Ids.requirePositive(command.screenId(), "Screen");
        Schedule.requireFuture(command.startTime(), LocalDateTime.now(clock));
        var price = new Price(command.basePrice());
        // Remote calls finish before acquiring a connection or any database locks.
        var movie = movies.getMovie(command.movieId());
        var venue = cinemas.getVenue(command.screenId(), command.cinemaId());
        return Schedule.plan(movie, venue, command.startTime(), price, LocalDateTime.now(clock));
    }
}
