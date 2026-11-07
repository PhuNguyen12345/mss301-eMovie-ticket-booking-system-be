package fu.mss301.booking.application.port;

import java.util.Map;

/** Names a ticket shows, translated from cinema-service. */
public interface CinemaCatalog {
    String cinemaName(Integer cinemaId);

    String screenName(Integer screenId);

    /** Seat type names keyed by seat type id. */
    Map<Integer, String> seatTypeNames();
}
