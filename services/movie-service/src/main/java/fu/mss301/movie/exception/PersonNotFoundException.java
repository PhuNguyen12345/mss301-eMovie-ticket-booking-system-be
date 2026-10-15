package fu.mss301.movie.exception;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(Integer id) {
        super("Person not found with ID: " + id);
    }
}
