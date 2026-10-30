package fu.mss301.booking.infrastructure.client;

import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
class ShowtimeCatalogAdapter implements ShowtimeCatalog {
    private final ShowtimeClient client;

    ShowtimeCatalogAdapter(ShowtimeClient client) {
        this.client = client;
    }

    @Override
    public ShowtimeSnapshot getShowtime(Long showtimeId) {
        var showtime = Upstream.call(() -> client.getShowtime(showtimeId), "Showtime");
        if (!showtimeId.equals(showtime.id()) || showtime.screenId() == null || showtime.startTime() == null
                || showtime.basePrice() == null || showtime.basePrice().signum() < 0 || showtime.status() == null) {
            throw new UpstreamServiceException("Showtime service returned invalid showtime data");
        }
        return new ShowtimeSnapshot(showtime.id(), showtime.screenId(), showtime.startTime(), showtime.basePrice(),
            "SCHEDULED".equals(showtime.status()));
    }

    @Override
    public Map<Integer, SeatTypePrice> getSeatPrices(Long showtimeId) {
        try {
            return Upstream.call(() -> client.getSeatPrices(showtimeId), "Showtime seat prices").stream()
                .filter(p -> p.seatTypeId() != null && p.price() != null && p.price().signum() >= 0)
                .collect(Collectors.toMap(ShowtimeClient.SeatPriceInfo::seatTypeId,
                    p -> new SeatTypePrice(p.id(), p.seatTypeId(), p.price()), (first, second) -> first));
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) return Map.of(); // per-type pricing not offered yet
            throw exception;
        }
    }
}
