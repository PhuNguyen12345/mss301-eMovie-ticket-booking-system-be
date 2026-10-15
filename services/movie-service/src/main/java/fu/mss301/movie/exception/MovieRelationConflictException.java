package fu.mss301.movie.exception;

public class MovieRelationConflictException extends RuntimeException {
    public MovieRelationConflictException(String message) {
        super(message);
    }
}
