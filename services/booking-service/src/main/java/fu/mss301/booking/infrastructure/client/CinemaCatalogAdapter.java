package fu.mss301.booking.infrastructure.client;

import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.port.CinemaCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
class CinemaCatalogAdapter implements CinemaCatalog {
    private final CinemaClient client;

    CinemaCatalogAdapter(CinemaClient client) {
        this.client = client;
    }

    @Override
    public String cinemaName(Integer cinemaId) {
        return requireName(Upstream.call(() -> client.getCinema(cinemaId), "Cinema").name());
    }

    @Override
    public String screenName(Integer screenId) {
        return requireName(Upstream.call(() -> client.getScreen(screenId), "Screen").name());
    }

    @Override
    public Map<Integer, String> seatTypeNames() {
        return Upstream.call(client::getSeatTypes, "Seat types").stream()
            .filter(type -> type.id() != null && type.name() != null)
            .collect(Collectors.toMap(CinemaClient.SeatTypeInfo::id, CinemaClient.SeatTypeInfo::name,
                (first, second) -> first));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new UpstreamServiceException("Cinema service returned invalid data");
        return name;
    }
}
