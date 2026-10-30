package fu.mss301.booking.application.booking;

import java.math.BigDecimal;
import java.util.List;

public record SeatMapView(Long showtimeId, Integer screenId, List<SeatAvailability> seats) {
    public enum Availability { AVAILABLE, HELD, BOOKED, UNAVAILABLE }

    public record SeatAvailability(Integer seatId, String rowCode, Integer seatNumber, Integer seatTypeId,
                                   BigDecimal price, Availability status) {}
}
