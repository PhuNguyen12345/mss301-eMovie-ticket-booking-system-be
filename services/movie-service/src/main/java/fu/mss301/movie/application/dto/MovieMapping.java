package fu.mss301.movie.application.dto;
import fu.mss301.movie.domain.model.Movie;
import fu.mss301.movie.domain.model.MovieDetails;
public final class MovieMapping {
    private MovieMapping() {}
    public static MovieDetails details(MovieRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Movie request is required");
        return new MovieDetails(request.getTitle(), request.getDescription(), request.getDurationMinutes(),
                request.getReleaseDate(), request.getLanguage(), request.getAgeRating(), request.getPosterUrl(),
                request.getTrailerUrl(), request.getStatus());
    }
    public static MovieResponseDTO view(Movie movie) {
        var d = movie.details();
        return new MovieResponseDTO(movie.id(), d.title(), d.description(), d.durationMinutes(), d.releaseDate(),
                d.language(), d.ageRating(), d.posterUrl(), d.trailerUrl(), d.status(), movie.createdAt(), movie.updatedAt());
    }
}
