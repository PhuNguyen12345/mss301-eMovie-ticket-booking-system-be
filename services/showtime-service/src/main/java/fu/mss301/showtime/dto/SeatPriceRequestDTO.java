package fu.mss301.showtime.dto;

import java.math.BigDecimal;

public record SeatPriceRequestDTO(Integer seatTypeId, BigDecimal price) {}
