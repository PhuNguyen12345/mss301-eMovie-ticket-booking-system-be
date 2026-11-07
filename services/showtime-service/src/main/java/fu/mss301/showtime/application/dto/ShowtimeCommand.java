package fu.mss301.showtime.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeCommand(
    Integer movieId,
    Integer cinemaId,
    Integer screenId,
    LocalDateTime startTime,
    BigDecimal basePrice
) {}
