package fu.mss301.movie.infrastructure.persistence.mapper;

import fu.mss301.movie.domain.model.Credit;
import fu.mss301.movie.domain.model.Movie;
import fu.mss301.movie.domain.model.MovieDetails;
import java.util.List;
public final class MovieMapper {
    private MovieMapper() {}
    public static Movie domain(fu.mss301.movie.infrastructure.persistence.entity.Movie e,
            List<Integer> genres, List<Credit> credits) {
        return Movie.restore(e.getId(),
                new MovieDetails(e.getTitle(), e.getDescription(), e.getDurationMinutes(), e.getReleaseDate(),
                        e.getLanguage(), e.getAgeRating(), e.getPosterUrl(), e.getTrailerUrl(),
                        e.getStatus()),
                e.getPosterPublicId(), e.getCreatedAt(), e.getUpdatedAt(), genres, credits);
    }
    public static void details(Movie value, fu.mss301.movie.infrastructure.persistence.entity.Movie e) {
        var d = value.details();
        e.setTitle(d.title());
        e.setDescription(d.description());
        e.setDurationMinutes(d.durationMinutes());
        e.setReleaseDate(d.releaseDate());
        e.setLanguage(d.language());
        e.setAgeRating(d.ageRating());
        e.setPosterUrl(d.posterUrl());
        e.setTrailerUrl(d.trailerUrl());
        e.setStatus(d.status());
    }
}
