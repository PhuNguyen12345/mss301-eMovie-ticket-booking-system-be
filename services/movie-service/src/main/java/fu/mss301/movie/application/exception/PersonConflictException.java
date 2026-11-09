package fu.mss301.movie.application.exception;

public class PersonConflictException extends RuntimeException {
    public PersonConflictException(String message) {
        super(message);
    }
}
