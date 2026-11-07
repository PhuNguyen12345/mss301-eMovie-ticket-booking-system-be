package fu.mss301.showtime.infrastructure.client;

import fu.mss301.showtime.application.port.CinemaCatalog;
import fu.mss301.showtime.application.exception.UpstreamServiceException;
import fu.mss301.showtime.domain.model.Venue;
import org.springframework.stereotype.Component;

@Component
public class FeignCinemaCatalog implements CinemaCatalog {
    private final CinemaClient client;
    public FeignCinemaCatalog(CinemaClient client) { this.client = client; }

    public Venue getVenue(Integer screenId, Integer cinemaId) {
        var screen = UpstreamResponses.get(() -> client.getScreen(screenId), "Screen");
        var cinema = UpstreamResponses.get(() -> client.getCinema(cinemaId), "Cinema");
        try {
            if (!screenId.equals(screen.id()) || !cinemaId.equals(cinema.id()) || screen.status() == null
                    || cinema.status() == null) throw new IllegalArgumentException();
            return new Venue(screenId, screen.cinemaId(), cinemaId, cinema.location(),
                "ACTIVE".equals(screen.status()), "ACTIVE".equals(cinema.status()));
        } catch (IllegalArgumentException exception) {
            throw new UpstreamServiceException("Cinema service returned invalid cinema or screen data");
        }
    }
}
