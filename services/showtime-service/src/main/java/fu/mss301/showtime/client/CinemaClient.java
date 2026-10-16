package fu.mss301.showtime.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cinema-service", url = "${showtime.clients.cinema-url:}")
public interface CinemaClient {
    @GetMapping("/api/screens/{id}")
    ApiResponse<ScreenInfo> getScreen(@PathVariable("id") Integer id);

    @GetMapping("/api/cinemas/{id}")
    ApiResponse<CinemaInfo> getCinema(@PathVariable("id") Integer id);

    record ScreenInfo(Integer id, Integer cinemaId, String status) {}
    record CinemaInfo(Integer id, String location, String status) {}
}
