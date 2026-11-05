package fu.mss301.showtime.domain.model;

import java.time.LocalDate;

public record Movie(Integer id, int durationMinutes, LocalDate releaseDate, String status) {
    public Movie {
        Ids.requirePositive(id, "Movie");
        if (durationMinutes <= 0 || releaseDate == null ||
                !("PRESENTING".equals(status) || "WILL_PRESENT".equals(status) || "EXPIRED".equals(status))) {
            throw new IllegalArgumentException("Invalid movie data");
        }
    }

    public void requireAvailableOn(LocalDate date) {
        if ("EXPIRED".equals(status) || date.isBefore(releaseDate)) {
            throw new IllegalArgumentException("Movie must be available and released by the showtime date");
        }
    }
}
