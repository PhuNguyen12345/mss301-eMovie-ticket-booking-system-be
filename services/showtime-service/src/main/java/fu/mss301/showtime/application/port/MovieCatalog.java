package fu.mss301.showtime.application.port;
import fu.mss301.showtime.domain.model.Movie;
public interface MovieCatalog {
    Movie getMovie(Integer id);
}
