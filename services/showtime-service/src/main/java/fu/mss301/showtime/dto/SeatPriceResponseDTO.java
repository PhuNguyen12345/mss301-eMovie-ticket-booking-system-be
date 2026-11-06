package fu.mss301.showtime.dto;

import java.math.BigDecimal;

public record SeatPriceResponseDTO(Integer id, Integer seatTypeId, BigDecimal price) {}
