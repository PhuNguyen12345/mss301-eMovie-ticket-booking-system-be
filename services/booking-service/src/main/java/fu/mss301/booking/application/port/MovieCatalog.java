package fu.mss301.booking.application.port;

/** What a ticket needs to know about a movie, translated from movie-service. */
public interface MovieCatalog {
    MovieInfo getMovie(Integer movieId);

    /** @param posterUrl null when the movie has no poster */
    record MovieInfo(String title, String posterUrl) {}
}
