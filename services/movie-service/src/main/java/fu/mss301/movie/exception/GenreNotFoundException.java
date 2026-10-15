package fu.mss301.movie.exception;

public class GenreNotFoundException extends RuntimeException {
    public GenreNotFoundException(Integer id) {
        super("Genre not found with ID: " + id);
    }
}
