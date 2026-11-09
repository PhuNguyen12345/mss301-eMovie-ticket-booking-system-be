package fu.mss301.movie.application.exception;

public class GenreNotFoundException extends RuntimeException {
    public GenreNotFoundException(Integer id) {
        super("Genre not found with ID: " + id);
    }
}
