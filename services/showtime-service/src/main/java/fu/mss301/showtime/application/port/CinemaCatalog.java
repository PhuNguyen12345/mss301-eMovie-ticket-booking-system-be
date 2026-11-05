package fu.mss301.showtime.application.port;
import fu.mss301.showtime.domain.model.Venue;
public interface CinemaCatalog {
    Venue getVenue(Integer screenId, Integer cinemaId);
}
