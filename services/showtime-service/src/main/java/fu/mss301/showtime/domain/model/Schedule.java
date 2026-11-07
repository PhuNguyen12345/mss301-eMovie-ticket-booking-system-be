package fu.mss301.showtime.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record Schedule(Integer movieId, Integer cinemaId, Integer screenId, String cinemaLocation,
                       LocalDateTime startTime, LocalDateTime endTime, Price basePrice) {
    public void requireAvailable(boolean overlaps) {
        if (overlaps) throw new fu.mss301.showtime.domain.exception.ShowtimeConflictException(
            "Screen already has a showtime in this time range");
    }

    public Schedule {
        Ids.requirePositive(movieId, "Movie");
        Ids.requirePositive(cinemaId, "Cinema");
        Ids.requirePositive(screenId, "Screen");
        Objects.requireNonNull(basePrice, "Base price is required");
        if (cinemaLocation == null || cinemaLocation.isBlank() || cinemaLocation.length() > 255) {
            throw new IllegalArgumentException("Invalid cinema location");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)
                || startTime.getYear() < 1000 || startTime.getYear() > 9998 || endTime.getYear() > 9999
                || startTime.getNano() % 1000 != 0 || endTime.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Invalid showtime interval");
        }
    }

    public static Schedule plan(Movie movie, Venue venue, LocalDateTime start, Price price, LocalDateTime now) {
        requireFuture(start, now);
        movie.requireAvailableOn(start.toLocalDate());
        venue.requireBookable();
        return new Schedule(movie.id(), venue.cinemaId(), venue.screenId(), venue.location(), start,
            start.plusMinutes(movie.durationMinutes()), price);
    }

    public static void requireFuture(LocalDateTime start, LocalDateTime now) {
        if (start == null || !start.isAfter(now) || start.getYear() > 9998 || start.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Start time must be in the future, before year 9999, with at most microsecond precision");
        }
    }
}
