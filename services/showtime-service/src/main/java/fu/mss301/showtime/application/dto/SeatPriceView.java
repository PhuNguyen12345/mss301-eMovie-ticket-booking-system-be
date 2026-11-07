package fu.mss301.showtime.application.dto;

import java.math.BigDecimal;

public record SeatPriceView(Integer id, Integer seatTypeId, BigDecimal price) {}
