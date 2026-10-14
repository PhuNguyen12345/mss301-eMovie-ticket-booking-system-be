package fu.mss301.movie.exception;

public class MovieNotFoundException extends RuntimeException {
    public MovieNotFoundException(Integer id) {
        super("Movie not found with ID: " + id); 
    }
    
}
