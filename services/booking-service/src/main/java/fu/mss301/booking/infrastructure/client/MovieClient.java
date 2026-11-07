package fu.mss301.booking.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "movie-service", url = "${booking.clients.movie-url:}")
public interface MovieClient {
    @GetMapping("/api/movies/{id}")
    ApiResponse<MovieInfo> getMovie(@PathVariable("id") Integer id);

    record MovieInfo(String title, String posterUrl) {}
}
