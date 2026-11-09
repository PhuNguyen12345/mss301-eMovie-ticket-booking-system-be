package fu.mss301.movie.domain.model;

import fu.mss301.movie.domain.exception.MovieRelationConflictException;
import java.time.LocalDateTime;
import java.util.*;

public final class Movie {
    private final Integer id;
    private MovieDetails details;
    private String posterPublicId;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private List<Integer> genreIds;
    private List<Credit> credits;

    private Movie(Integer id, MovieDetails details, String posterPublicId, LocalDateTime createdAt,
            LocalDateTime updatedAt, List<Integer> genreIds, List<Credit> credits) {
        this.id = id;
        this.details = details;
        this.posterPublicId = posterPublicId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.genreIds = List.copyOf(genreIds);
        this.credits = List.copyOf(credits);
    }
    public static Movie create(MovieDetails details) {
        return new Movie(null, details.validated(), null, null, null, List.of(), List.of());
    }
    public static Movie restore(Integer id, MovieDetails details, String posterPublicId,
            LocalDateTime createdAt, LocalDateTime updatedAt, List<Integer> genreIds, List<Credit> credits) {
        return new Movie(id, details, posterPublicId, createdAt, updatedAt, genreIds, credits);
    }
    public void revise(MovieDetails replacement) {
        details = replacement.validated();
    }
    public void changeStatus(MovieStatus status) {
        details = details.withStatus(status);
    }
    public String replacePoster(String url, String publicId) {
        String previous = posterPublicId;
        details = details.withPoster(url);
        posterPublicId = publicId;
        return previous;
    }
    public static List<Integer> validateGenres(List<Integer> replacement) {
        if (replacement == null)
            throw new IllegalArgumentException("genreIds is required; use [] to clear genres");
        Set<Integer> unique = new TreeSet<>();
        for (Integer id : replacement) {
            if (id == null || id <= 0)
                throw new IllegalArgumentException("Genre ID must be positive");
            if (!unique.add(id))
                throw new IllegalArgumentException("Duplicate genre ID: " + id);
        }
        return List.copyOf(unique);
    }
    public void replaceGenres(List<Integer> replacement) {
        genreIds = validateGenres(replacement);
    }
    public static List<Credit> validateCredits(List<Credit> replacement) {
        if (replacement == null)
            throw new IllegalArgumentException("credits is required; use [] to clear credits");
        Set<Credit.Key> keys = new HashSet<>();
        List<Credit> validated = new ArrayList<>();
        for (Credit credit : replacement) {
            if (credit == null)
                throw new IllegalArgumentException("Credit item must not be null");
            Credit next = credit.validated();
            if (!keys.add(next.key()))
                throw new IllegalArgumentException("Duplicate person and credit type");
            validated.add(next);
        }
        return List.copyOf(validated);
    }
    public void replaceCredits(List<Credit> replacement) {
        List<Credit> validated = validateCredits(replacement);
        Map<Credit.Key, Integer> ids = new HashMap<>();
        for (Credit credit : credits) {
            if (ids.containsKey(credit.key()))
                throw new MovieRelationConflictException("Existing credits contain duplicate person and "
                                                         + "credit type; resolve duplicates first");
            ids.put(credit.key(), credit.id());
        }
        credits = validated.stream()
                          .map(c
                                  -> new Credit(ids.get(c.key()), c.personId(), c.creditType(),
                                          c.characterName(), c.displayOrder()))
                          .toList();
    }
    public Integer id() {
        return id;
    }
    public MovieDetails details() {
        return details;
    }
    public String posterPublicId() {
        return posterPublicId;
    }
    public LocalDateTime createdAt() {
        return createdAt;
    }
    public LocalDateTime updatedAt() {
        return updatedAt;
    }
    public List<Integer> genreIds() {
        return genreIds;
    }
    public List<Credit> credits() {
        return credits;
    }
}
