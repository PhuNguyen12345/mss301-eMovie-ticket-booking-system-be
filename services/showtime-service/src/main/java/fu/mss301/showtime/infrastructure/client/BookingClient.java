package fu.mss301.showtime.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "booking-service", url = "${showtime.clients.booking-url:}")
public interface BookingClient {
    @PostMapping("/internal/showtimes/{id}/cancellation")
    ApiResponse<CancellationResult> showtimeCancelled(@PathVariable("id") Long id);

    record CancellationResult(Long showtimeId, int cancelledPending, int cancelledPaid, int failed) {}
}
