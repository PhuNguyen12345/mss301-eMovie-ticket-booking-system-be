package fu.mss301.movie.domain.model;

import java.time.LocalDate;

public record MovieDetails(String title, String description, Integer durationMinutes, LocalDate releaseDate,
        String language, String ageRating, String posterUrl, String trailerUrl, MovieStatus status) {
    public MovieDetails validated() {
        if (title == null || title.isBlank() || title.trim().length() > 255)
            throw new IllegalArgumentException("Title is required and must not exceed 255 characters");
        if (durationMinutes == null || durationMinutes <= 0)
            throw new IllegalArgumentException("Duration must be greater than 0");
        if (releaseDate == null)
            throw new IllegalArgumentException("Release date is required");
        if (status == null)
            throw new IllegalArgumentException("Movie status is required");
        length(language, 50, "Language");
        length(ageRating, 20, "Age rating");
        length(posterUrl, 500, "Poster URL");
        length(trailerUrl, 500, "Trailer URL");
        return new MovieDetails(title.trim(), description, durationMinutes, releaseDate, language, ageRating,
                posterUrl, trailerUrl, status);
    }
    private static void length(String value, int limit, String field) {
        if (value != null && value.length() > limit)
            throw new IllegalArgumentException(field + " must not exceed " + limit + " characters");
    }
    public MovieDetails withStatus(MovieStatus value) {
        if (value == null)
            throw new IllegalArgumentException("Movie status is required");
        return new MovieDetails(title, description, durationMinutes, releaseDate, language, ageRating,
                posterUrl, trailerUrl, value);
    }
    public MovieDetails withPoster(String value) {
        return new MovieDetails(title, description, durationMinutes, releaseDate, language, ageRating, value,
                trailerUrl, status);
    }
}
