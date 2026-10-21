package fu.mss301.showtime.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.LocalDate;

@FeignClient(name = "movie-service", url = "${showtime.clients.movie-url:}")
public interface MovieClient {
    @GetMapping("/api/movies/{id}")
    ApiResponse<MovieInfo> getMovie(@PathVariable("id") Integer id);

    record MovieInfo(Integer id, Integer durationMinutes, LocalDate releaseDate, String status) {}
}
