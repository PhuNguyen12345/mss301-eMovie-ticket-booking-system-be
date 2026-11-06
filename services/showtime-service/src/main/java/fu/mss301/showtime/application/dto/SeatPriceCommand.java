package fu.mss301.showtime.application.dto;

import java.math.BigDecimal;

public record SeatPriceCommand(Integer seatTypeId, BigDecimal price) {}
