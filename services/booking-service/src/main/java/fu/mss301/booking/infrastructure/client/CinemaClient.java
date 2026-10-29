package fu.mss301.booking.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "cinema-service", url = "${booking.clients.cinema-url:}")
public interface CinemaClient {
    /** Proposed contract for cinema-service: the physical seats of one screen. */
    @GetMapping("/api/screens/{screenId}/seats")
    ApiResponse<List<SeatInfo>> getSeats(@PathVariable("screenId") Integer screenId);

    record SeatInfo(Integer id, Integer screenId, String rowCode, Integer seatNumber, Integer seatTypeId, String status) {}
}
