package fu.mss301.booking.application.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** What booking needs to know about a showtime, translated from showtime-service. */
public interface ShowtimeCatalog {
    ShowtimeSnapshot getShowtime(Long showtimeId);

    /** Seat-type prices keyed by seat type id; empty when showtime-service has no per-type pricing. */
    Map<Integer, SeatTypePrice> getSeatPrices(Long showtimeId);

    record ShowtimeSnapshot(Long id, Integer screenId, LocalDateTime startTime, BigDecimal basePrice, boolean scheduled) {
        public boolean isOpenForBookingAt(LocalDateTime now) {
            return scheduled && startTime.isAfter(now);
        }
    }

    /** @param id id of the showtime_seat_prices row, stored on booking_seats.showtime_seat_id */
    record SeatTypePrice(Integer id, Integer seatTypeId, BigDecimal price) {}
}
