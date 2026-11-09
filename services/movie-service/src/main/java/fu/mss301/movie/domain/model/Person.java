package fu.mss301.movie.domain.model;

public record Person(Integer id, String fullName) {
    public static Person create(String name) {
        return new Person(null, name).rename(name);
    }
    public Person rename(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Full name is required");
        if (name.trim().length() > 255)
            throw new IllegalArgumentException("Full name must not exceed 255 characters");
        return new Person(id, name.trim());
    }
}
