package fu.mss301.cinema.application.usecase;

public class CinemaNotFoundException extends RuntimeException {
    public CinemaNotFoundException(String resource, int id) { super(resource + " " + id + " does not exist"); }
}
