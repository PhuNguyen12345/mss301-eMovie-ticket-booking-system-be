package fu.mss301.showtime.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record SeatPrice(Integer id, Integer seatTypeId, Price price, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public SeatPrice {
        if (id != null) Ids.requirePositive(id, "Seat price");
        Ids.requirePositive(seatTypeId, "Seat type");
        Objects.requireNonNull(price);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    static SeatPrice create(SeatPriceSelection selection, LocalDateTime now) {
        return new SeatPrice(null, selection.seatTypeId(), selection.price(), now, now);
    }

    SeatPrice reprice(Price value, LocalDateTime now) {
        return new SeatPrice(id, seatTypeId, value, createdAt, now);
    }
}
