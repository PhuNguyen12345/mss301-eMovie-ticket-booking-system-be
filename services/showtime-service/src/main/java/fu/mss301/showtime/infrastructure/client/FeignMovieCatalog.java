package fu.mss301.showtime.infrastructure.client;

import fu.mss301.showtime.application.port.MovieCatalog;
import fu.mss301.showtime.application.exception.UpstreamServiceException;
import fu.mss301.showtime.domain.model.Movie;
import org.springframework.stereotype.Component;

@Component
public class FeignMovieCatalog implements MovieCatalog {
    private final MovieClient client;
    public FeignMovieCatalog(MovieClient client) { this.client = client; }

    public Movie getMovie(Integer id) {
        var value = UpstreamResponses.get(() -> client.getMovie(id), "Movie");
        try {
            if (!id.equals(value.id()) || value.durationMinutes() == null) throw new IllegalArgumentException();
            return new Movie(value.id(), value.durationMinutes(), value.releaseDate(), value.status());
        } catch (IllegalArgumentException exception) {
            throw new UpstreamServiceException("Movie service returned invalid movie data");
        }
    }
}
