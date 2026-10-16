package fu.mss301.showtime.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeRequestDTO(
    Integer movieId,
    Integer cinemaId,
    Integer screenId,
    LocalDateTime startTime,
    BigDecimal basePrice
) {}
