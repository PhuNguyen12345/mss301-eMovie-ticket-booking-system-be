package fu.mss301.movie.application.exception;

public class GenreConflictException extends RuntimeException {
    public GenreConflictException(String message) {
        super(message);
    }
}
