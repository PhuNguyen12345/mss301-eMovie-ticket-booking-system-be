package fu.mss301.movie.domain.model;

public record Genre(Integer id, String name, String description) {
    public static Genre create(String name, String description) {
        return new Genre(null, name, description).rename(name, description);
    }
    public Genre rename(String name, String description) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Genre name is required");
        if (name.trim().length() > 100)
            throw new IllegalArgumentException("Genre name must not exceed 100 characters");
        if (description != null && description.length() > 500)
            throw new IllegalArgumentException("Description must not exceed 500 characters");
        return new Genre(id, name.trim(), description);
    }
}
