package fu.mss301.movie.domain.exception;

public class MovieRelationConflictException extends RuntimeException {
    public MovieRelationConflictException(String message) {
        super(message);
    }
}
