package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;

import java.math.BigDecimal;

/**
 * A seat the customer picked, already priced by the application layer.
 *
 * @param showtimeSeatId id of the showtime seat-price row the price came from (null when the showtime base price was used)
 */
public record SeatSelection(Integer seatId, Integer showtimeSeatId, BigDecimal price) {
    public SeatSelection {
        if (seatId == null || seatId <= 0) throw new DomainException("Seat id must be positive");
        if (price == null || price.signum() < 0 || price.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Seat price must be non-negative with at most 2 decimal places");
        }
    }
}
