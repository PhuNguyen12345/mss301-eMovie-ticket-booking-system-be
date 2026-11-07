package fu.mss301.booking.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "cinema-service", url = "${booking.clients.cinema-url:}")
public interface CinemaClient {
    @GetMapping("/api/screens/{screenId}/seats")
    ApiResponse<List<SeatInfo>> getSeats(@PathVariable("screenId") Integer screenId);

    @GetMapping("/api/cinemas/{id}")
    ApiResponse<NamedInfo> getCinema(@PathVariable("id") Integer id);

    @GetMapping("/api/screens/{id}")
    ApiResponse<NamedInfo> getScreen(@PathVariable("id") Integer id);

    @GetMapping("/api/seat-types")
    ApiResponse<List<SeatTypeInfo>> getSeatTypes();

    record NamedInfo(String name) {}

    record SeatTypeInfo(Integer id, String name) {}

    record SeatInfo(Integer id, Integer screenId, String rowCode, Integer seatNumber, Integer seatTypeId, String status) {}
}
