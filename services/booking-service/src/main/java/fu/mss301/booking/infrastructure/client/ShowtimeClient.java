package fu.mss301.booking.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = "showtime-service", url = "${booking.clients.showtime-url:}")
public interface ShowtimeClient {
    @GetMapping("/api/showtimes/{id}")
    ApiResponse<ShowtimeInfo> getShowtime(@PathVariable("id") Long id);

    /** Proposed contract (showtime_seat_prices); booking falls back to basePrice while it does not exist. */
    @GetMapping("/api/showtimes/{id}/seat-prices")
    ApiResponse<List<SeatPriceInfo>> getSeatPrices(@PathVariable("id") Long id);

    record ShowtimeInfo(Long id, Integer screenId, LocalDateTime startTime, BigDecimal basePrice, String status) {}

    record SeatPriceInfo(Integer id, Integer seatTypeId, BigDecimal price) {}
}
