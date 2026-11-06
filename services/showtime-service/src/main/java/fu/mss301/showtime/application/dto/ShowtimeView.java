package fu.mss301.showtime.application.dto;

import fu.mss301.showtime.domain.model.ShowtimeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeView(
    Long id,
    Integer movieId,
    Integer cinemaId,
    Integer screenId,
    String cinemaLocation,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BigDecimal basePrice,
    ShowtimeStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
 ) {
    public static ShowtimeView from(fu.mss301.showtime.domain.model.Showtime showtime) {
        var schedule = showtime.schedule();
        return new ShowtimeView(showtime.id(), schedule.movieId(), schedule.cinemaId(), schedule.screenId(),
            schedule.cinemaLocation(), schedule.startTime(), schedule.endTime(), schedule.basePrice().amount(),
            showtime.status(), showtime.createdAt(), showtime.updatedAt());
    }
}
