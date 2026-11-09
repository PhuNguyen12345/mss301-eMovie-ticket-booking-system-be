package fu.mss301.movie.application.exception;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(Integer id) {
        super("Person not found with ID: " + id);
    }
}
