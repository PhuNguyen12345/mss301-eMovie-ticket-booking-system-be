package fu.mss301.showtime.dto;

import fu.mss301.showtime.enums.ShowtimeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeResponseDTO(
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
) {}
