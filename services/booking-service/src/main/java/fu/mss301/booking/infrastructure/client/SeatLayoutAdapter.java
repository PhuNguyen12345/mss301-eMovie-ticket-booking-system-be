package fu.mss301.booking.infrastructure.client;

import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SeatLayoutAdapter implements SeatLayoutProvider {
    private final CinemaClient client;

    SeatLayoutAdapter(CinemaClient client) {
        this.client = client;
    }

    @Override
    public List<PhysicalSeat> getSeats(Integer screenId) {
        return Upstream.call(() -> client.getSeats(screenId), "Screen seats").stream()
            .map(seat -> {
                if (seat.id() == null || (seat.screenId() != null && !seat.screenId().equals(screenId))) {
                    throw new UpstreamServiceException("Cinema service returned invalid seat data");
                }
                return new PhysicalSeat(seat.id(), seat.rowCode(), seat.seatNumber(), seat.seatTypeId(),
                    "ACTIVE".equals(seat.status()));
            })
            .toList();
    }
}
