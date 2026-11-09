package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;

import java.math.BigDecimal;

/**
 * A seat the customer picked, already priced by the application layer.
 *
 * @param showtimeSeatId id of the showtime seat-price row the price came from (null when the showtime base price was used)
 * @param seatTypeName null when the seat has no known type
 * @param seatTypeId null when the seat has no type
 */
public record SeatSelection(Integer seatId, Integer showtimeSeatId, BigDecimal price, String rowCode,
                            Integer seatNumber, String seatTypeName, Integer seatTypeId) {
    public SeatSelection {
        if (seatId == null || seatId <= 0) throw new DomainException("Seat id must be positive");
        if (price == null || price.signum() < 0 || price.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Seat price must be non-negative with at most 2 decimal places");
        }
    }
}
