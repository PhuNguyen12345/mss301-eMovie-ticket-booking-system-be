package fu.mss301.booking.infrastructure.client;

import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.port.MovieCatalog;
import org.springframework.stereotype.Component;

@Component
class MovieCatalogAdapter implements MovieCatalog {
    private final MovieClient client;

    MovieCatalogAdapter(MovieClient client) {
        this.client = client;
    }

    @Override
    public MovieInfo getMovie(Integer movieId) {
        var movie = Upstream.call(() -> client.getMovie(movieId), "Movie");
        if (movie.title() == null || movie.title().isBlank()) {
            throw new UpstreamServiceException("Movie service returned invalid movie data");
        }
        return new MovieInfo(movie.title(), movie.posterUrl());
    }
}
