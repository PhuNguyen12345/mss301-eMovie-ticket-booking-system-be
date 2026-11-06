package fu.mss301.showtime.application.dto;
import fu.mss301.showtime.domain.model.Ids;
import fu.mss301.showtime.domain.model.ShowtimeStatus;
import java.time.LocalDate;
public record ShowtimeSearch(Integer movieId, Integer cinemaId, Integer screenId, LocalDate date,
                             String location, ShowtimeStatus status, int page, int size) {
    public ShowtimeSearch {
        if (movieId != null) Ids.requirePositive(movieId, "Movie");
        if (cinemaId != null) Ids.requirePositive(cinemaId, "Cinema");
        if (screenId != null) Ids.requirePositive(screenId, "Screen");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        if (date != null && (date.getYear() < 1000 || date.getYear() > 9998)) {
            throw new IllegalArgumentException("Date is outside the supported range");
        }
        if (location != null && location.trim().length() > 255) {
            throw new IllegalArgumentException("Location must not exceed 255 characters");
        }
    }
}
